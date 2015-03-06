/**
 * Copyright 2005-2015 hdiv.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hdiv.config.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hdiv.config.factory.ValidationRepositoryFactoryBean;
import org.hdiv.config.factory.ValidationRepositoryFactoryBean.ValidationTargetData;
import org.hdiv.config.validations.DefaultValidationParser;
import org.hdiv.validator.DefaultEditableDataValidationProvider;
import org.hdiv.validator.IValidation;
import org.hdiv.validator.Validation;
import org.hdiv.validator.ValidationRepository;
import org.hdiv.web.validator.EditableParameterValidator;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ListFactoryBean;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.ClassUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * BeanDefinitionParser for &lt;hdiv:editableValidations&gt; element.
 */
public class EditableValidationsBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	public static final String EDITABLE_VALIDATION_PROVIDER_BEAN_NAME = "org.hdiv.validator.EditableDataValidationProvider";

	public static final String DEFAULT_EDITABLE_VALIDATIONS_BEAN_NAME = "org.hdiv.defaultEditableValidations";

	public static final String EDITABLE_VALIDATOR_BEAN_NAME = "hdivEditableValidator";

	/**
	 * Is Spring MVC in classpath?
	 */
	private final boolean springMvcPresent = ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet",
			EditableValidationsBeanDefinitionParser.class.getClassLoader());

	/**
	 * Is JSR303 library in classpath?
	 */
	private static final boolean jsr303Present = ClassUtils.isPresent("javax.validation.Validator",
			EditableValidationsBeanDefinitionParser.class.getClassLoader());

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.beans.factory.xml.AbstractBeanDefinitionParser#resolveId(org.w3c.dom.Element,
	 * org.springframework.beans.factory.support.AbstractBeanDefinition,
	 * org.springframework.beans.factory.xml.ParserContext)
	 */
	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext)
			throws BeanDefinitionStoreException {

		return EDITABLE_VALIDATION_PROVIDER_BEAN_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser#getBeanClass(org.w3c.dom.Element)
	 */
	protected Class<?> getBeanClass(Element element) {
		return DefaultEditableDataValidationProvider.class;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser#doParse(org.w3c.dom.Element,
	 * org.springframework.beans.factory.xml.ParserContext,
	 * org.springframework.beans.factory.support.BeanDefinitionBuilder)
	 */
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder bean) {

		Object source = parserContext.extractSource(element);

		Map<ValidationTargetData, List<String>> validationsData = new LinkedHashMap<ValidationTargetData, List<String>>();

		RuntimeBeanReference repositoryRef = this.createValidationRepository(element, source, parserContext,
				validationsData);
		bean.getBeanDefinition().getPropertyValues().addPropertyValue("validationRepository", repositoryRef);

		// Register default editable validation
		boolean registerDefaults = true;
		Node named = element.getAttributes().getNamedItem("registerDefaults");
		if (named != null) {
			String registerDefaultsValue = named.getTextContent();
			if (registerDefaultsValue != null) {
				registerDefaults = Boolean.TRUE.toString().equalsIgnoreCase(registerDefaultsValue);
			}
		}

		if (registerDefaults) {
			// Create beans for default validations
			createDefaultEditableValidations(element, parserContext);
		}

		NodeList list = element.getChildNodes();

		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				if (node.getLocalName().equalsIgnoreCase("validationRule")) {

					this.processValidationRule(node, bean, validationsData);
				}
			}
		}

		if (this.springMvcPresent) {
			parserContext.getRegistry().registerBeanDefinition(EDITABLE_VALIDATOR_BEAN_NAME,
					this.createValidator(element, source, parserContext));
		}
	}

	protected RuntimeBeanReference createValidationRepository(Element element, Object source,
			ParserContext parserContext, Map<ValidationTargetData, List<String>> validationsData) {

		RootBeanDefinition bean = new RootBeanDefinition(ValidationRepositoryFactoryBean.class);
		bean.setSource(source);
		bean.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		RuntimeBeanReference beanRef = new RuntimeBeanReference(ConfigBeanDefinitionParser.PATTERN_MATCHER_FACTORY_NAME);
		bean.getPropertyValues().addPropertyValue("patternMatcherFactory", beanRef);

		bean.getPropertyValues().addPropertyValue("validationsData", validationsData);

		String name = ValidationRepository.class.getName();
		parserContext.getRegistry().registerBeanDefinition(name, bean);
		return new RuntimeBeanReference(name);
	}

	/**
	 * Initialize Map with url, parameter and ValidationRule data.
	 * 
	 * @param node
	 *            processing xml node
	 * @param bean
	 *            bean configuration
	 * @param validationsData
	 *            Map with url, parameter and ValidationRule data
	 */
	protected void processValidationRule(Node node, BeanDefinitionBuilder bean,
			Map<ValidationTargetData, List<String>> validationsData) {

		String value = node.getTextContent();
		List<String> ids = this.convertToList(value);

		NamedNodeMap attributes = node.getAttributes();
		Node urlNode = attributes.getNamedItem("url");
		Node paramsNode = attributes.getNamedItem("parameters");

		if (urlNode != null || paramsNode != null) {
			ValidationTargetData data = new ValidationTargetData();
			if (urlNode != null) {
				String url = urlNode.getTextContent();
				data.setUrl(url);
			}
			if (paramsNode != null) {
				String params = paramsNode.getTextContent();
				List<String> paramsList = convertToList(params);
				data.setParams(paramsList);
			}

			boolean enableDefaults = true;
			Node named = attributes.getNamedItem("enableDefaults");
			if (named != null) {
				String enableDefaultsVal = named.getTextContent();
				if (enableDefaultsVal != null) {
					enableDefaults = Boolean.TRUE.toString().equalsIgnoreCase(enableDefaultsVal);
				}
			}

			if (enableDefaults) {
				// Add defaults
				ids.add(DEFAULT_EDITABLE_VALIDATIONS_BEAN_NAME);
			}

			validationsData.put(data, ids);
		}
	}

	/**
	 * Convert String with bean id's in List
	 * 
	 * @param data
	 *            String data
	 * @return List with bean id's
	 */
	protected List<String> convertToList(String data) {
		if (data == null || data.length() == 0) {
			return new ArrayList<String>();
		}
		String[] result = data.split(",");
		List<String> list = new ArrayList<String>();
		for (String val : result) {
			list.add(val.trim());
		}
		return list;
	}

	/**
	 * Create beans for the default editable validations.
	 * 
	 * @param element
	 *            xml element
	 * @param parserContext
	 *            xml parser context
	 */
	protected void createDefaultEditableValidations(Element element, ParserContext parserContext) {

		// Load validations from xml
		DefaultValidationParser parser = new DefaultValidationParser();
		parser.readDefaultValidations();
		List<Map<String, String>> validations = parser.getValidations();

		List<IValidation> defaultValidations = new ArrayList<IValidation>();

		for (Map<String, String> validation : validations) {
			// Map contains validation id and regex extracted from the xml
			String id = validation.get("id");
			String regex = validation.get("regex");

			// Create validation instance
			Validation validationBean = new Validation();
			validationBean.setName(id);
			validationBean.setDefaultValidation(true);
			validationBean.setRejectedPattern(regex);

			defaultValidations.add(validationBean);
		}

		Object source = parserContext.extractSource(element);
		RootBeanDefinition bean = new RootBeanDefinition(ListFactoryBean.class);
		bean.setSource(source);
		bean.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		bean.getPropertyValues().addPropertyValue("sourceList", defaultValidations);

		// Register validation list bean
		parserContext.getRegistry().registerBeanDefinition(DEFAULT_EDITABLE_VALIDATIONS_BEAN_NAME, bean);

	}

	protected RootBeanDefinition createValidator(Element element, Object source, ParserContext parserContext) {
		RootBeanDefinition bean = new RootBeanDefinition(EditableParameterValidator.class);
		bean.setSource(source);
		bean.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		if (jsr303Present) {

			RootBeanDefinition validatorDef = new RootBeanDefinition(LocalValidatorFactoryBean.class);
			validatorDef.setSource(source);
			validatorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String validatorName = parserContext.getReaderContext().registerWithGeneratedName(validatorDef);
			parserContext.registerComponent(new BeanComponentDefinition(validatorDef, validatorName));

			bean.getPropertyValues().addPropertyValue("innerValidator", new RuntimeBeanReference(validatorName));
		}
		return bean;
	}

}