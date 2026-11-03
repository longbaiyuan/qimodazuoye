package com.example.qimo.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class DatabaseInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Database.ensureInitialized();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
