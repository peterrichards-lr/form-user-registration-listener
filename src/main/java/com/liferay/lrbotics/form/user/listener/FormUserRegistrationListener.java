package com.liferay.lrbotics.form.user.listener;

import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.account.service.CommerceAccountLocalService;
import com.liferay.commerce.account.service.CommerceAccountUserRelLocalService;
import com.liferay.commerce.account.service.CommerceAccountUserRelLocalServiceWrapper;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldType;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.Contact;
import com.liferay.portal.kernel.model.ListType;
import com.liferay.portal.kernel.model.ListTypeConstants;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ContactLocalService;
import com.liferay.portal.kernel.service.ListTypeLocalServiceUtil;
import com.liferay.portal.kernel.service.ListTypeServiceUtil;
import com.liferay.portal.kernel.service.PhoneLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Richards
 */
@Component(immediate = true, configurationPid = FormUserRegistrationListenerConfiguration.PID, service = ModelListener.class)
public class FormUserRegistrationListener extends BaseModelListener<DDMFormInstanceRecordVersion> {

	@Override
	public void onAfterCreate(DDMFormInstanceRecordVersion formRecord) throws ModelListenerException {
		if (!_config.enabled()) {
			_log.debug("The form listener is disabled");
			super.onAfterCreate(formRecord);
			return;
		}

		_log.debug("Processing create event");
		super.onAfterCreate(formRecord);
	}

	/**
	 * Listen to form records updates and add or update a new user depending on the
	 * form ID and the form's workflow status.
	 *
	 * @param formRecord a form submission
	 * @throws ModelListenerException if the form record cannot be updated
	 * @see FormUserAccountListenerConfiguration
	 */
	@Override
	public void onAfterUpdate(DDMFormInstanceRecordVersion formRecord) throws ModelListenerException {
		if (!_config.enabled()) {
			_log.debug("The form listener is disabled");
			super.onAfterUpdate(formRecord);
			return;
		}

		_log.debug("Processing update event");

		final long formId = _config.formId();
		if (formId != formRecord.getFormInstanceId()) {
			_log.debug("The expected form ID from the configuration is {}", formId);
			_log.debug("The actual form ID here is {}", formRecord.getFormInstanceId());
			return;
		}

		Locale locale;
		try {
			locale = getLocale(formRecord);
		} catch (PortalException e1) {
			_log.debug("Unable to determine locale, so default will be used");
			locale = LocaleUtil.getDefault();
		}
		
		final long companyId = getCompanyId(formRecord);

		switch (formRecord.getStatus()) {
		case WorkflowConstants.STATUS_PENDING:
			_log.debug("Form in pending state");

			// If user should be created on pending then do it now, otherwise, it will be
			// done at approval
			if (_config.createOnPending()) {
				Map<String, String> fields;
				try {
					fields = getFormFieldsAsMap(formRecord);
				} catch (PortalException e) {
					_log.error("Unable to map form fields", e);
					return;
				}

				addInactiveUser(companyId, fields, locale);
			}
			break;
		case WorkflowConstants.STATUS_APPROVED:
			_log.debug("Form in approved state");

			Map<String, String> fields;
			try {
				fields = getFormFieldsAsMap(formRecord);
			} catch (PortalException e) {
				_log.error("Unable to map form fields", e);
				return;
			}

			// If the record was not created on pending then let's do it now
			if (!_config.createOnPending()) {
				addInactiveUser(companyId, fields, locale);

			}
			activateUser(companyId, fields);

			break;
		default:
			_log.debug("Unsupported workflow state - {}", formRecord.getStatus());
			break;
		}

		super.onAfterUpdate(formRecord);
	}

	/**
	 * Add a user with the status 'inactive' when a form to create an account is
	 * submitted for publication.
	 */
	private void addInactiveUser(final long companyId, final Map<String, String> fields, final Locale locale) {

		final ServiceContext serviceContext = new ServiceContext();
		final boolean autoPassword = true;
		final boolean autoScreenName = true;
		final long suffixId = -1L;
		final boolean male = false;
		final Calendar dob = new GregorianCalendar(1970, 1, 1);
		final boolean sendEmail = false;

		final String jobRole = getJobRole(fields);
		final String jobTitle = getValueOrDefault(getJobTitle(fields), jobRole);

		final String title = getTitle(fields);
		final long prefixId = title.equals("Other") ? getPrefixId(getTitleOther(fields)) : getPrefixId(title);

		_log.debug("Title {}[{}]", title, prefixId);

		User newUser;
		try {
			newUser = _userLocalService.addUser(getCreatorUserId(companyId), companyId, autoPassword, null, null,
					autoScreenName, null, getEmailAddress(fields), locale, getForename(fields), null,
					getSurname(fields), prefixId, suffixId, male, (dob.get(Calendar.MONTH) - 1), dob.get(Calendar.DATE),
					dob.get(Calendar.YEAR), jobTitle, null, null, null, null, sendEmail, serviceContext);
			newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
			_userLocalService.updateUser(newUser);
		} catch (PortalException e) {
			_log.error("Unable to add user", e);
			return;
		}

		setJobRole(newUser.getContactId(), getJobRole(fields));
		addPhoneNumber(newUser.getUserId(), newUser.getContactId(), getPhoneNumber(fields), serviceContext);

		setCommerceAccount(getCompanyName(fields), newUser.getUserId());

		_log.debug("New pending user from user account creation form: {}", newUser);

	}

	/**
	 * Update an inactive user with the status 'active' if the form submission has
	 * been approved.
	 */
	private void activateUser(final long companyId, final Map<String, String> fields) {

		try {
			User user;
			user = _userLocalService.getUserByEmailAddress(companyId, getEmailAddress(fields));
			user.setStatus(WorkflowConstants.STATUS_APPROVED);
			_userLocalService.updateUser(user);
		} catch (PortalException e) {
			_log.error("Unable to ativate user", e);
			return;
		}
	}

	/**
	 * Transform form names and values from a form record to a map.
	 *
	 * @param formRecord a form submission
	 * @return a map with fields name as key and fields value as value
	 * @throws PortalException if the form values can't be parsed properly
	 */
	private Map<String, String> getFormFieldsAsMap(DDMFormInstanceRecordVersion formRecord) throws PortalException {

		final Locale locale = formRecord.getDDMForm().getDefaultLocale();
		final List<DDMFormFieldValue> fields = formRecord.getDDMFormValues().getDDMFormFieldValues();
		final Map<String, String> formFields = new HashMap<>();

		_log.debug("Transform form fields to map (id={})", formRecord.getFormInstanceId());

		fields.forEach(ddmFormFieldValue -> {
			mapFormFields(locale, ddmFormFieldValue, formFields);
		});
		return formFields;
	}

	private void mapFormFields(Locale locale, DDMFormFieldValue fieldValue, Map<String, String> map) {
		if (fieldValue == null) {
			return;
		}

		final String type = fieldValue.getType();
		if (DDMFormFieldType.FIELDSET.equals(type)) {
			_log.debug("Recursive call {}", fieldValue.getName());
			fieldValue.getNestedDDMFormFieldValues()
					.forEach(nestedFieldValue -> mapFormFields(locale, nestedFieldValue, map));
			return;
		}

		final String key = fieldValue.getFieldReference();
		final String initialValue = fieldValue.getValue().getString(locale);

		if (DDMFormFieldType.SELECT.equals(type) || DDMFormFieldType.RADIO.equals(type)) {
			final String normalisedValue = initialValue.replace("[\"", "").replace("\"]", "");
			final String decodedValue = fieldValue.getDDMFormField().getDDMFormFieldOptions()
					.getOptionReference(normalisedValue);
			_log.debug("Field -> {}[{}]={}", key, type, decodedValue);
			map.put(key, decodedValue);
		} else {
			_log.debug("Field -> {}[{}]={}", key, type, initialValue);
			map.put(key, initialValue);
		}
	}

	private void setCommerceAccount(final String companyName, final long userId) {
		if (Validator.isBlank(companyName)) {
			return;
		}

		try {
			final ClassLoader classLoader = _commerceAccountLocalService.getClass().getClassLoader();
			final DynamicQuery accountQuery = DynamicQueryFactoryUtil.forClass(CommerceAccount.class, classLoader);
			accountQuery.add(RestrictionsFactoryUtil.eq("name", companyName.toLowerCase()));

			List<CommerceAccount> accounts = _commerceAccountLocalService.dynamicQuery(accountQuery);

			if (accounts.isEmpty()) {
				_log.debug("No accounts found for {}", companyName);
			} else if (accounts.size() == 1) {
				CommerceAccount commerceAccount = accounts.get(0);
				final long commeceAccountId = commerceAccount.getPrimaryKey();
				_log.debug("Matched to {}[{}]", commerceAccount.getName(), commeceAccountId);
				_log.debug("Proceeding to assign {} to {}", userId, commeceAccountId);
				ServiceContext context = new ServiceContext();
				context.setUserId(userId);
				_commerceAccountUserRelLocalService.addCommerceAccountUserRel(commeceAccountId,
						commerceAccount.getUserId(), context);
			} else {
				_log.debug("Too many matches");
				accounts.forEach(a -> {
					_log.debug("{}", a.toString());
				});
			}
		} catch (Exception e) {
			_log.error("Oh no", e);
			return;
		}
	}

	private long getPrefixId(final String prefix) {
		return Optional.ofNullable(prefix).map(p -> {
			ListType listType = ListTypeLocalServiceUtil.addListType(p.toLowerCase(), ListTypeConstants.CONTACT_PREFIX);
			return listType.getPrimaryKey();
		}).orElse(0L);
	}

	private void addPhoneNumber(final long userId, final long contactId, final String phoneNumber,
			final ServiceContext serviceContext) {
		if (Validator.isBlank(phoneNumber)) {
			return;
		}

		final Contact contact = _contactLocalService.fetchContact(contactId);
		if (contact == null) {
			return;
		}

		final String modelClassName = contact.getModelClass().getName();
		final long phoneTypeId = ListTypeServiceUtil.getListTypes(ListTypeConstants.CONTACT_PHONE).get(0)
				.getListTypeId();

		try {
			if (serviceContext == null) {
				_phoneLocalService.addPhone(userId, modelClassName, contact.getPrimaryKey(), phoneNumber, "",
						phoneTypeId, false, new ServiceContext());
			} else {
				_phoneLocalService.addPhone(userId, modelClassName, contact.getPrimaryKey(), phoneNumber, "",
						phoneTypeId, false, serviceContext);
			}
		} catch (PortalException e) {
			_log.error("Unable to add phone number", e);
			return;
		}
	}

	private void setJobRole(final long contactId, final String jobRole) {
		if (Validator.isBlank(jobRole)) {
			return;
		}

		final Contact contact = _contactLocalService.fetchContact(contactId);
		if (contact == null) {
			return;
		}

		contact.setJobClass(jobRole);
		_contactLocalService.updateContact(contact);
	}

	private <T> T getValueOrDefault(T value, T defaultValue) {
		return value == null ? defaultValue : value;
	}

	private long getCreatorUserId(final long companyId) throws PortalException {
		return _companyLocalService.getCompany(companyId).getDefaultUser().getUserId();
	}

	private long getCompanyId(final DDMFormInstanceRecordVersion formRecord) {
		return formRecord.getCompanyId();
	}

	private Locale getLocale(final DDMFormInstanceRecordVersion formRecord) throws PortalException {
		return formRecord.getDDMForm().getDefaultLocale();
	}

	private String getTitle(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.titleFieldId());
	}

	private String getTitleOther(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.titleOtherFieldId());
	}

	private String getForename(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.forenameFieldId());
	}

	private String getSurname(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.surnameFieldId());
	}

	private String getEmailAddress(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.emailAddressFieldId());
	}

	private String getPhoneNumber(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.phoneNumberFieldId());
	}

	private String getJobTitle(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.jobTitleFieldId());
	}

	private String getJobRole(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.jobRoleFieldId());
	}

	private String getCompanyName(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.companyNameFieldId());
	}

	private String getCompanyNumber(final Map<String, String> formFieldMap) {
		return getFieldValue(formFieldMap, _config.companyNameFieldId());
	}

	private String getFieldValue(final Map<String, String> formFieldMap, final String key) {
		return formFieldMap.getOrDefault(key, "");
	}

	@Activate
	@Modified
	public void activate(Map<String, String> properties) {

		_config = ConfigurableUtil.createConfigurable(FormUserRegistrationListenerConfiguration.class, properties);
	}

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private CommerceAccountLocalService _commerceAccountLocalService;

	@Reference
	private CommerceAccountUserRelLocalService _commerceAccountUserRelLocalService;

	@Reference
	private ContactLocalService _contactLocalService;

	@Reference
	private PhoneLocalService _phoneLocalService;

	@Reference
	private UserLocalService _userLocalService;

	private volatile FormUserRegistrationListenerConfiguration _config;

	private static final Logger _log = LoggerFactory.getLogger(FormUserRegistrationListener.class);
}