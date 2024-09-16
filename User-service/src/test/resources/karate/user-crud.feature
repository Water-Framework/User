# Generated with Water Generator
# The Goal of feature test is to ensure the correct format of json responses
# If you want to perform functional test please refer to ApiTest

Feature: Check User Rest Api Response

  Scenario: Water User CRUD operations

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url 'http://localhost:8080/water/users'
    # ---- Add entity fields here -----
    And request
    """
    {
       "username": "username1",
       "name":"name",
       "lastname":"lastName",
       "password":"Password1.",
       "passwordConfirm":"Password1.",
       "email":"user@mail.com"
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
        "email":"user@mail.com",
        "username": "username1",
        "admin": false,
        "imagePath":null
       }
    """
    * def entityId = response.id

    # --------------- UPDATE -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url 'http://localhost:8080/water/users'
    # ---- Add entity fields here -----
    And request
    """
    {
       "id": "#(entityId)",
       "username": "usernameUpdated",
       "name":"name",
       "lastname":"lastName",
       "password":"Password1",
       "passwordConfirm":"Password1.",
       "email":"user@mail.com"
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
        "email":"user@mail.com",
        "username": "usernameUpdated",
        "admin": false,
        "imagePath":null
       }
    """
    # --------------- FIND -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url 'http://localhost:8080/water/users/'+entityId
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
        "email":"user@mail.com",
        "username": "usernameUpdated",
        "admin": false,
        "imagePath":null
       }
    """

    # --------------- FIND ALL -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url 'http://localhost:8080/water/users'
    When method GET
    Then status 200
    And match response ==
    """
      {
        "numPages":1,
        "currentPage":1,
        "nextPage":1,
        "delta":20,
        "results":[
         {
            "id": #number,
            "entityVersion":1,
            "entityCreateDate":'#number',
            "entityModifyDate":'#number',
            "name":'Admin',
            "lastname":"Admin",
            "email":"hadmin@water.it",
            "username": "admin",
            "admin": true,
            "imagePath":null
          },
          {
            "id": #number,
            "entityVersion":2,
            "entityCreateDate":'#number',
            "entityModifyDate":'#number',
            "name":'name',
            "lastname":"lastName",
            "email":"user@mail.com",
            "username": "usernameUpdated",
            "admin": false,
            "imagePath":null
          }
        ]
      }
    """

    # --------------- DELETE -----------------------------

    Given header Content-Type = 'application/json'
    And header Accept = 'application/json'
    Given url 'http://localhost:8080/water/users/'+entityId
    When method DELETE
    # 204 because delete response is empty, so the status code is "no content" but is ok
    Then status 204