package com.liferay.lrbotics.form.user.listener;

import com.liferay.portal.kernel.settings.definition.ConfigurationBeanDeclaration;

import org.osgi.service.component.annotations.Component;

@Component
public class FormUserRegistrationListenerConfigurationBeanDeclaration implements ConfigurationBeanDeclaration {

	@Override
	public Class<?> getConfigurationBeanClass() {
		return FormUserRegistrationListenerConfiguration.class;
	}

}
