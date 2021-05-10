## Form listener for customer registration

In a B2B scenario there is often a process where customers need to register their interest and to meet predefined criteria in order to be approved for portal membership.

This is a model listener which hooks into a Liferay DXP form and workflow. The configuration of this module allows it to be enabled / disabled and to listen only to a specific form instance. It is possible to define whether the user account should not be created until approval has been received or if an inactive account should be created prior to approval and then for the approval process to activate the account.

In addition, the configuration allows for the default field mappings to be overridden so the user data can be correctly extracted from the form.
