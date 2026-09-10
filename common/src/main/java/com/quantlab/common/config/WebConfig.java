package com.quantlab.common.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI resources

        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173/","http://localhost:5174/","https://algo.dhanistocks.com",
                        "https://*.dhanistocks.com","https://newdsmobileuat.dhanistocks.com","https://indiabulls.quantlabdemo.com/",
                        "https://algo-qa.dhanistocks.com/",
                        "https://algo-qa.ibullssecurities.com/", "https://algo-qa.ibullssecurities.com",
                        "https://algo.ibullssecurities.com/", "https://algo.ibullssecurities.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
