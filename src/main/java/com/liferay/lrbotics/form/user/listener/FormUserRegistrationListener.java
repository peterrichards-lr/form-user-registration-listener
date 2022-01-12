package com.liferay.lrbotics.form.user.listener;

import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.account.service.CommerceAccountLocalService;
import com.liferay.commerce.account.service.CommerceAccountUserRelLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldType;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Contact;
import com.liferay.portal.kernel.model.ListType;
import com.liferay.portal.kernel.model.ListTypeConstants;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ContactLocalService;
import com.liferay.portal.kernel.service.ListTypeLocalServiceUtil;
import com.liferay.portal.kernel.service.ListTypeServiceUtil;
import com.liferay.portal.kernel.service.PhoneLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Richards
 */
@Component(immediate = true, configurationPid = FormUserRegistrationListenerConfiguration.PID, service = ModelListener.class)
public class FormUserRegistrationListener extends BaseModelListener<DDMFormInstanceRecordVersion> {

	private FormUserRegistrationListenerConfiguration getConfig(DDMFormInstanceRecordVersion formRecord) {
		final long groupId = formRecord.getGroupId();
		try {
			return _configurationProvider
					.getGroupConfiguration(
							FormUserRegistrationListenerConfiguration.class,
							groupId);
		} catch (ConfigurationException e) {
			_log.error("Error while getting group / site configuration", e);
			return null;
		}
	}
	
	@Override
	public void onAfterCreate(DDMFormInstanceRecordVersion formRecord) throws ModelListenerException {
		final FormUserRegistrationListenerConfiguration config = getConfig(formRecord);
		
		if (config == null) {
			_log.debug("Unable to obtain form listener configuration");
		} else if (!config.enabled()) {
			_log.debug("The form listener is disabled");
		} else {
			_log.debug("Processing create event");			
		}

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
		final FormUserRegistrationListenerConfiguration config = getConfig(formRecord);

		if (config == null) {
			_log.debug("Unable to obtain form listener configuration");
		} else if (!config.enabled()) {
			_log.debug("The form listener is disabled");
		} else {
			_log.debug("Processing update event");
			processUpdateEvent(formRecord, config);
		}
		super.onAfterUpdate(formRecord);
	}

	private void processUpdateEvent(DDMFormInstanceRecordVersion formRecord,
			final FormUserRegistrationListenerConfiguration config) {

		final long formId = config.formId();
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
		final Company company = getCompany(companyId);
		final long creatorUserId = getCreatorUserId(company);
		final long groupId = getGroupId(formRecord);
		final String portalURL = getPortalURL(company, groupId, config);

		_log.debug("companyId: {}", companyId);
		_log.debug("creatorUserId: {}", creatorUserId);
		_log.debug("groupId: {}", groupId);
		_log.debug("portalURL: {}", portalURL);

		final ServiceContext serviceContext =
				ServiceContextThreadLocal.getServiceContext();
		
		HttpServletRequest httpServletRequest =
				serviceContext.getRequest();

		if (httpServletRequest == null) {
			_log.warn("serviceContext.getRequest() returned null");
		}

		ThemeDisplay themeDisplay = serviceContext.getThemeDisplay();
		
		if (themeDisplay == null) {
			_log.warn("serviceContext.getThemeDisplay() returned null");			
		} else {
			_log.debug("themeDisplay.getSiteGroupId(): {}", themeDisplay.getSiteGroupId());
		}
		
		if (httpServletRequest != null)  {
			httpServletRequest.setAttribute(
					WebKeys.THEME_DISPLAY, themeDisplay);			
			serviceContext.setRequest(httpServletRequest);
			
			_log.debug("Added ThemeDisplay to request attributes and added the request to the ServiceContext");
		}
		
		_log.debug("serviceContext.getCompanyId(): {}", serviceContext.getCompanyId());
		_log.debug("serviceContext.getUserId(): {}", serviceContext.getUserId());
		_log.debug("serviceContext.getScopeGroupId(): {}", serviceContext.getScopeGroupId());
		_log.debug("serviceContext.getPortalURL(): {}", serviceContext.getPortalURL());
		
		_log.debug("serviceContext.getPortletId(): {}", serviceContext.getPortletId());
		
		try {
			_log.debug("serviceContext.getPortletPreferencesIds(): {}", serviceContext.getPortletPreferencesIds());
		} catch (NullPointerException npe) {
			// If there is an issue getting the portlet preference IDs then reset portlet ID as this is used by
			// serviceContext.getPortletPreferencesIds() to determine when to get them from the HttpServletRequest
			// This is called when the serviceContext is cloned.
			_log.debug("serviceContext.getPortletPreferencesIds() threw NullPointerException so resetting portlet ID");
			serviceContext.setPortletId(null);
		}
		
		serviceContext.setCompanyId(companyId);
		serviceContext.setUserId(creatorUserId);
		serviceContext.setPortalURL(portalURL);
		serviceContext.setScopeGroupId(groupId);
		
		switch (formRecord.getStatus()) {
		case WorkflowConstants.STATUS_PENDING:
			_log.debug("Form in pending state");

			// If user should be created on pending then do it now, otherwise, it will be
			// done at approval
			if (config.createOnPending()) {
				Map<String, String> fields;
				try {
					fields = getFormFieldsAsMap(formRecord);
				} catch (PortalException e) {
					_log.error("Unable to map form fields", e);
					return;
				}

				addInactiveUser(companyId, fields, locale, serviceContext, config);
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
			if (!config.createOnPending()) {
				addInactiveUser(companyId, fields, locale, serviceContext, config);

			}
			activateUser(companyId, fields, serviceContext, config);

			break;
		default:
			_log.debug("Unsupported workflow state - {}", formRecord.getStatus());
			break;
		}
	}

	/**
	 * Add a user with the status 'inactive' when a form to create an account is
	 * submitted for publication.
	 */
	private void addInactiveUser(final long companyId, final Map<String, String> fields, final Locale locale,
			final ServiceContext serviceContext, final FormUserRegistrationListenerConfiguration config) {

		final boolean autoPassword = true;
		final boolean autoScreenName = true;
		final long suffixId = -1L;
		final boolean male = false;
		final Calendar dob = new GregorianCalendar(1970, 1, 1);
		
		final boolean sendEmail = config.sendEmail();
		final String emailAddress = getEmailAddress(fields, config);
		
		User user = _userLocalService.fetchUserByEmailAddress(
				companyId, emailAddress);

		if (user != null) {
			_log.warn("A user already exists with email address {}", emailAddress);
			return;
		}

		_log.debug(sendEmail ? "An email will be sent to the user at {}" : "No email will be sent to the user", emailAddress);

		final String jobRole = getJobRole(fields, config);
		final String jobTitle = getValueOrDefault(getJobTitle(fields, config), jobRole);

		final String title = getTitle(fields, config);
		final long prefixId = title.equals("Other") ? getPrefixId(getTitleOther(fields, config)) : getPrefixId(title);

		_log.trace("Title {}[{}]", title, prefixId);

		final long[] siteIds = getSiteMembershipIds(config);

		User newUser;
		try {
			newUser = _userLocalService.addUser(serviceContext.getUserId(), companyId, autoPassword, null, null,
					autoScreenName, null, emailAddress, locale, getForename(fields, config), null, getSurname(fields, config), prefixId,
					suffixId, male, (dob.get(Calendar.MONTH) - 1), dob.get(Calendar.DATE), dob.get(Calendar.YEAR),
					jobTitle, siteIds, null, null, null, sendEmail, serviceContext);
			newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
			_userLocalService.updateUser(newUser);
		} catch (PortalException e) {
			_log.error("Unable to add user", e);
			return;
		}

		setJobRole(newUser.getContactId(), getJobRole(fields, config));
		addPhoneNumber(newUser.getUserId(), newUser.getContactId(), getPhoneNumber(fields, config), serviceContext);

		final String companyName = getCompanyName(fields, config);
		if (!Validator.isBlank(companyName)) {
			CommerceAccount account = lookupCommerceAccount(companyName);

			try {
				if (config.createCommerceAccount() && account == null) {
					account = createCommerceAccount(companyName, getCompanyNumber(fields, config), emailAddress,
							newUser.getUserId(), serviceContext);
				} else {
					linkCommerceAccountToUser(account, newUser.getUserId());
				}
			} catch (PortalException e) {
				_log.error("Unable to create / link the commerce account", e);
			}
		}

		_log.debug("New pending user from user account creation form: {}", newUser);
	}

	/**
	 * Update an inactive user with the status 'active' if the form submission has
	 * been approved.
	 */
	private void activateUser(final long companyId, final Map<String, String> fields,
			final ServiceContext serviceContext, final FormUserRegistrationListenerConfiguration config) {

		try {
			User user;
			user = _userLocalService.getUserByEmailAddress(companyId, getEmailAddress(fields, config));
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

	private CommerceAccount createCommerceAccount(final String companyName, final String companyNumber,
			final String email, final long userId, final ServiceContext serviceContext) throws PortalException {
		return _commerceAccountLocalService.addBusinessCommerceAccount(companyName, -1, email, "", true, companyNumber,
				null, new String[] { email }, serviceContext);
	}

	private CommerceAccount lookupCommerceAccount(final String companyName) {
		CommerceAccount account = null;

		if (Validator.isBlank(companyName)) {
			return account;
		}

		try {
			final ClassLoader classLoader = _commerceAccountLocalService.getClass().getClassLoader();
			final DynamicQuery accountQuery = DynamicQueryFactoryUtil.forClass(CommerceAccount.class, classLoader);
			accountQuery.add(RestrictionsFactoryUtil.eq("name", companyName.toLowerCase()));

			List<CommerceAccount> accounts = _commerceAccountLocalService.dynamicQuery(accountQuery);

			if (accounts.size() == 1) {
				account = accounts.get(0);
				_log.debug("Found commerce account {}", account.getCommerceAccountId());
			} else if (accounts.isEmpty()) {
				_log.debug("No accounts found for {}", companyName);
			} else {
				_log.debug("Too many matches");
				if (_log.isDebugEnabled()) {
					accounts.forEach(a -> {
						_log.debug("{}", a.toString());
					});
				}
			}
		} catch (Exception e) {
			_log.error("Unable to lookup commerce account - " + companyName, e);
		}

		return account;
	}

	private void linkCommerceAccountToUser(final CommerceAccount account, final long userId) throws PortalException {
		if (account == null || userId < 1) {
			return;
		}

		final long commeceAccountId = account.getPrimaryKey();
		_log.debug("Matched to {}[{}]", account.getName(), commeceAccountId);
		_log.debug("Proceeding to assign {} to {}", userId, commeceAccountId);
		ServiceContext context = new ServiceContext();
		context.setUserId(account.getUserId());
		_commerceAccountUserRelLocalService.addCommerceAccountUserRel(commeceAccountId, userId, context);
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

	private long getCreatorUserId(final Company company) {
		try {
			final User defaultUser = company.getDefaultUser();
			if (defaultUser != null) {
				return defaultUser.getUserId();
			}
		} catch (PortalException e) {
			_log.warn("Unable to get the default user for company - {}", company.getCompanyId());
		}
		return -1;
	}

	private String getPortalURL(final Company company, final long groupId, final FormUserRegistrationListenerConfiguration config) {
		String url = null;
		try {
			if (Validator.isBlank(config.portalURL())) {
				_log.debug("portaLURL will not be overriden");
				url = company.getPortalURL(groupId);
			} else {
				_log.debug("portaLURL will be overriden");
				url = config.portalURL();
				if (url.endsWith("/")) {
					url = url.substring(0, url.length() - 1);
				}
			}
		} catch (PortalException e) {
			_log.warn("Unable to determine portal UR for group {}", groupId);
		}
		_log.debug("getPortalURL() - url: {}", url);
		return url;
	}

	private Company getCompany(final long companyId) {
		try {
			return _companyLocalService.getCompany(companyId);
		} catch (PortalException e) {
			_log.warn("Unable to fetch the company - {}", companyId);
			return null;
		}
	}

	private long[] getSiteMembershipIds(final FormUserRegistrationListenerConfiguration config) {
		if (Validator.isBlank(config.siteIds())) {
			return new long[0];
		}

		String siteIdsStr = config.siteIds();
		siteIdsStr = siteIdsStr.replaceAll("\\s+", "");
		return Stream.of(siteIdsStr.split(",")).mapToLong(Long::parseLong).toArray();
	}

	private long getCompanyId(final DDMFormInstanceRecordVersion formRecord) {
		return formRecord.getCompanyId();
	}

	private long getGroupId(final DDMFormInstanceRecordVersion formRecord) {
		return formRecord.getGroupId();
	}

	private Locale getLocale(final DDMFormInstanceRecordVersion formRecord) throws PortalException {
		return formRecord.getDDMForm().getDefaultLocale();
	}

	private String getTitle(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.titleFieldId());
	}

	private String getTitleOther(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.titleOtherFieldId());
	}

	private String getForename(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.forenameFieldId());
	}

	private String getSurname(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.surnameFieldId());
	}

	private String getEmailAddress(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.emailAddressFieldId());
	}

	private String getPhoneNumber(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.phoneNumberFieldId());
	}

	private String getJobTitle(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.jobTitleFieldId());
	}

	private String getJobRole(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.jobRoleFieldId());
	}

	private String getCompanyName(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.companyNameFieldId());
	}

	private String getCompanyNumber(final Map<String, String> formFieldMap, final FormUserRegistrationListenerConfiguration config) {
		return getFieldValue(formFieldMap, config.companyNumberFieldId());
	}

	private String getFieldValue(final Map<String, String> formFieldMap, final String key) {
		return formFieldMap.getOrDefault(key, "");
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

	@Reference
	private ConfigurationProvider _configurationProvider;
	
	private static final Logger _log = LoggerFactory.getLogger(FormUserRegistrationListener.class);
}