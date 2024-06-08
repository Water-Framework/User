
package it.water.user;

import com.intuit.karate.junit5.Karate;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.User;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.api.TestPermissionManager;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import lombok.Setter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WaterTestExtension.class)
public class UserRestApiTest implements Service {

    @Karate.Test
    Karate restInterfaceTest() {
        return Karate.run("classpath:karate");
    }

}
