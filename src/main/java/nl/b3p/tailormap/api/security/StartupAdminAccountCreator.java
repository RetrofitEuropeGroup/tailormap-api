/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Configuration
@Profile("!test")
public class StartupAdminAccountCreator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.new-admin-username:admin}")
  private String newAdminUsername;

  final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public StartupAdminAccountCreator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void postConstruct() throws IOException {
    logger.trace("Checking whether an admin or admin-users account exists...");

    InternalAdminAuthentication.setInSecurityContext();
    try {
      if (!userRepository.existsByGroupsNameIn(Arrays.asList(Group.ADMIN_USERS, Group.ADMIN))) {
        // Create a new admin-users account with a random generated password
        String password = UUID.randomUUID().toString();

        User u =
            new User().setUsername(newAdminUsername).setPassword(passwordEncoder.encode(password));
        u.getGroups().add(new Group().setName(Group.ADMIN_USERS));
        userRepository.saveAndFlush(u);

        // Log generated password
        logger.info(getAccountBanner(newAdminUsername, password));
      }
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }

  private static String getAccountBanner(String username, String password) throws IOException {
    String accountBanner =
        StreamUtils.copyToString(
            new ClassPathResource("account-banner.txt").getInputStream(),
            StandardCharsets.US_ASCII);
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(new AnsiPropertySource("ansi", true));
    sources.addFirst(
        new MapPropertySource(
            "account",
            Map.of(
                "username", username,
                "password", password)));
    return new PropertySourcesPropertyResolver(sources).resolvePlaceholders(accountBanner);
  }
}
