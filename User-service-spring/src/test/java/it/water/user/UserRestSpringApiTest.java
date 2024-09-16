
package it.water.user;

import com.intuit.karate.junit5.Karate;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.User;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.user.UserManager;
import it.water.core.security.model.principal.UserPrincipal;
import it.water.implementation.spring.security.SpringSecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

@SpringBootTest(classes = UserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
        "water.rest.security.jwt.validate=false",
        "water.testMode=true"
})
public class UserRestSpringApiTest {
    @Autowired
    private ComponentRegistry componentRegistry;

    @BeforeEach
    void impersonateAdmin(){
        //jwt token service is disabled, we just inject admin user for bypassing permission system
        //just remove this line if you want test with permission system working
        fillSecurityContextWithAdmin();
    }

    @Karate.Test
    Karate restInterfaceTest() {
        return Karate.run("../User-service/src/test/resources/karate");
    }

    private void fillSecurityContextWithAdmin(){
        Runtime runtime = this.componentRegistry.findComponent(Runtime.class, null);
        UserManager userManager = this.componentRegistry.findComponent(UserManager.class, null);
        User u = userManager.findUser("admin");
        UserPrincipal userPrincipal = new UserPrincipal("admin",true,1,User.class.getName());
        runtime.fillSecurityContext(new SpringSecurityContext(Collections.singleton(userPrincipal)));
    }

}
