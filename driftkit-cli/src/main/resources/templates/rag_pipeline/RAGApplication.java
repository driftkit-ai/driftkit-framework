package /*PACKAGE_NAME*/;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RAGApplication {

    public static void main(String[] args) {
        SpringApplication.run(RAGApplication.class, args);
    }
}