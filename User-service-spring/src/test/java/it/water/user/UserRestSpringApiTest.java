
package it.water.user;

import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = UserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class UserRestSpringApiTest {

    @Karate.Test
    Karate restInterfaceTest() {
        return Karate.run("../User-service/src/test/resources/karate");
    }

}
