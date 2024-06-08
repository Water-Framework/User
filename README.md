# User Module

## Description

The “User” module exposes a whole set of features for user management. It is not a module that integrates login but exposes methods for retrieving the user given username and password.
The reason is that the login logic should be centralized in a separate module.

- User registration
- User management (activation/deactivation)
- Email confirmation on registration
- Password reset process (it generates a reset pwd code sent by email)
- Unregister process (it generates a confirmation code sent by email)

## UML Design

![User-Module](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/Water-Framework/User/develop/doc/uml/user.puml)

## Available Configurations

There are different possible configurations, the following are the main ones:

- specify whether registration should be active or not
- property defining account activation url
- property defining url for password rest.
- property to enable/disable physical deletion of account
- property defining mail template name to be used for registration.

Users can be enabled/disabled.

## Available Actions And Roles

The actions available on this entity are:

- **SAVE** : save a new user
- **UPDATE**: update an existing user
- **DELETE**: delete a user
- **FIND** : find a user
- **FIND ALL**: find all users
- **IMPERSONATE** : impersonate a user
- **ACTIVATE**: activate a user
- **DEACTIVATE**: deactivate a user

The default roles for user management are:

- **userManager**: SAVE,UPDATE,FIND,FIND_ALL,DELETE,IMPERSONATE,ACTIVATE,DEACTIVATE
- **userViewer**: FIND,FIND_ALL
- **userEditor**: SAVE,UPDATE,FIND,FIND_ALL

Basically “userManager” is the user admin. The viewer can only consult while the editor can consult and edit.

## Registration Email Template Management

The “User” module requires that an entity implementing the *en.water.core.api.notification.email.EmailNotificationService* interface be registered within the runtime.
That interface represents a bean capable, precisely, of sending the email. In addition, for template management , the same module, also provides for the existence of a bean that exposes the *it.water.core.api.notification.email.EmailContentBuilder* interface.
Both interfaces are available in the it.water.email module, but the developer, for example could implement on his own to hook into existing systems.


