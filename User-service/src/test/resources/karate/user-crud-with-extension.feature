# Generated with Water Generator
# The Goal of feature test is to ensure the correct format of json responses
# If you want to perform functional test please refer to ApiTest

Feature: Check User Extesion

  Scenario: Water User CRUD operations WITH Extension

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users'
    # ---- Add entity fields here -----
    And request
    """
    {
       "username": "usernameExtension",
       "name":"name",
       "lastname":"lastName",
       "password":"Password1.",
       "passwordConfirm":"Password1.",
       "email":"user-extension@mail.com",
       "extensionField1":"value1",
       "extensionField2":"value2"
     }
    """
    When method POST
    Then status 200
    # ---- Matching required response json ----
    And match response ==
    """
      { "id": #number,
        "entityVersion":1,
        "entityCreateDate":'#number',
        "entityModifyDate":'#number',
        "name":'name',
        "lastname":"lastName",
        "email":"user-extension@mail.com",
        "username": "usernameExtension",
        "admin": false,
        "imagePath":null,
        "extensionField1":"value1",
        "extensionField2":"value2"
       }
    """
    * def entityId = response.id

    # --------------- UPDATE -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users'
    # ---- Add entity fields here -----
    And request
    """
    {
       "id": "#(entityId)",
       "username": "usernameExtensionUpdated",
       "name":"name",
       "lastname":"lastName",
       "password":"Password1",
       "passwordConfirm":"Password1.",
       "email":"user-extension@mail.com",
       "extensionField1":"value1*",
       "extensionField2":"value2*",
     }
    """
    When method PUT
    Then status 200
    # ---- Matching required response json ----
    And match response ==
    """
      { "id": #number,
        "entityVersion":2,
        "entityCreateDate":'#number',
        "entityModifyDate":'#number',
        "name":'name',
        "lastname":"lastName",
        "email":"user-extension@mail.com",
        "username": "usernameExtensionUpdated",
        "admin": false,
        "imagePath":null,
        "extensionField1":"value1*",
        "extensionField2":"value2*",
       }
    """
    # --------------- FIND -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/'+entityId
    # ---------------------------------
    When method GET
    Then status 200
    # ---- Matching required response json ----
    And match response ==
     """
      { "id": #number,
        "entityVersion":2,
        "entityCreateDate":'#number',
        "entityModifyDate":'#number',
        "name":'name',
        "lastname":"lastName",
        "email":"user-extension@mail.com",
        "username": "usernameExtensionUpdated",
        "admin": false,
        "imagePath":null,
        "extensionField1":"value1*",
        "extensionField2":"value2*",
       }
    """

    # --------------- FIND ALL -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users'
    When method GET
    Then status 200
    And match response.results contains
    """
          {
            "id": #number,
            "entityVersion":2,
            "entityCreateDate":'#number',
            "entityModifyDate":'#number',
            "name":'name',
            "lastname":"lastName",
            "email":"user-extension@mail.com",
            "username": "usernameExtensionUpdated",
            "admin": false,
            "imagePath":null
          }
    """

    # --------------- DELETE -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/'+entityId
    When method DELETE
    # 204 because delete response is empty, so the status code is "no content" but is ok
    Then status 204