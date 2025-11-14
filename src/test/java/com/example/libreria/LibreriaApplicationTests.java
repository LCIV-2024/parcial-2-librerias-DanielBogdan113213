package com.example.libreria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "external.api.books.url=https://my-json-server.typicode.com/Gabriel-Arriola-UTN/libros/books"
})
class LibreriaApplicationTests {

    @Test
    void contextLoads() {
    }
}