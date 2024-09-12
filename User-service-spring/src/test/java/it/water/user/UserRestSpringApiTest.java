
package it.water.user;

import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = UserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
        "water.rest.security.jwt.validate=false"
})
public class UserRestSpringApiTest {

    @Karate.Test
    Karate restInterfaceTest() {
        return Karate.run("../User-service/src/test/resources/karate");
    }

}
