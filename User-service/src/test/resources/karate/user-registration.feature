# Generated with Water Generator
# The Goal of feature test is to ensure the correct format of json responses
# If you want to perform functional test please refer to ApiTest

Feature: Check User Rest Api Response
  Scenario: Register User
    * def username = "username"+randomSeed
    * def email = "username"+randomSeed+"@mail.com"
    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/register'
    # ---- Add entity fields here -----
    And request
    """
    {
       "username": "#(username)",
       "name":"name",
       "lastname":"lastName",
       "password":"Password1.",
       "passwordConfirm":"Password1.",
       "email":"#(email)"
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
        "email":"#(email)",
        "username": "#(username)",
        "admin": false,
        "imagePath":null
       }
    """
    * def entityId = response.id

    # --------------- ACTIVATING USER -----------------------------
    # Activation is possibile as admin since in rest test
    # Water framework starts automatically with admin permission
    # to let the developer test every method correctly

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/'+entityId+'/activate'
    # ---- Add entity fields here -----
    When method PUT
    Then status 204

    # --------------- RESET PASSWORD REQUEST -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/resetPasswordRequest?email='+email
    # ---- Add entity fields here -----
    When method PUT
    Then status 204
    # ---- Matching required response json ----

  # --------------- DEACTIVATE FROM ADMIN -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url serviceBaseUrl+'/water/users/'+entityId+'/deactivate'
    # ---- Add entity fields here -----
    When method PUT
    Then status 204
    # ---- Matching required response json ----

