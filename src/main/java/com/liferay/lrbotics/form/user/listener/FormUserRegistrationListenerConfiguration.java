
package com.liferay.lrbotics.form.user.listener;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition.Scope;

import aQute.bnd.annotation.metatype.Meta;
import aQute.bnd.annotation.metatype.Meta.Type;

/**
 * @author Peter Richards
 */
@ExtendedObjectClassDefinition(category = "liferaybotics-user-registration", scope = Scope.GROUP)

@Meta.OCD(id = FormUserRegistrationListenerConfiguration.PID, localization = "content/Language", name = "liferaybotics-user-registration-configuration-name")
public interface FormUserRegistrationListenerConfiguration {

	public static final String PID = "com.liferay.lrbotics.form.user.listener.FormUserRegistrationListenerConfiguration";

	@Meta.AD(required = false, name = "liferaybotics-user-registration-enable-listener", type = Type.Boolean, description = "liferaybotics-user-registration-enable-listener-description", deflt = "false")
	public boolean enabled();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-create-user-on-pending", type = Type.Boolean, description = "liferaybotics-user-registration-create-user-on-pending-description", deflt = "true")
	public boolean createOnPending();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-form-id", type = Type.Integer, description = "liferaybotics-user-registration-form-id-description")
	public long formId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-title-field-id", type = Type.String, description = "liferaybotics-user-registration-title-field-id-description", deflt = "title")
	public String titleFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-title-other-field-id", type = Type.String, description = "liferaybotics-user-registration-title-other-field-id-description", deflt = "titleOther")
	public String titleOtherFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-forename-field-id", type = Type.String, description = "liferaybotics-user-registration-forename-field-id-description", deflt = "forename")
	public String forenameFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-surname-field-id", type = Type.String, description = "liferaybotics-user-registration-surname-field-id-description", deflt = "surname")
	public String surnameFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-email-address-field-id", type = Type.String, description = "liferaybotics-user-registration-email-address-field-id-description", deflt = "emailAddress")
	public String emailAddressFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-phone-number-field-id", type = Type.String, description = "liferaybotics-user-registration-phone-number-field-id-description", deflt = "phoneNumber")
	public String phoneNumberFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-job-title-field-id", type = Type.String, description = "liferaybotics-user-registration-job-title-field-id-description", deflt = "jobTitle")
	public String jobTitleFieldId();
	
	@Meta.AD(required = false, name = "liferaybotics-user-registration-job-role-field-id", type = Type.String, description = "liferaybotics-user-registration-job-role-field-id-description", deflt = "jobRole")
	public String jobRoleFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-company-name-field-id", type = Type.String, description = "liferaybotics-user-registration-company-name-field-id-description", deflt = "companyName")
	public String companyNameFieldId();

	@Meta.AD(required = false, name = "liferaybotics-user-registration-company-number-field-id", type = Type.String, description = "liferaybotics-user-registration-company-number-field-id-description", deflt = "companyNumber")
	public String companyNumberFieldId();
}
