package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.Url;
import com.example.urlshortener.domain.User;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.repository.UserRepository;

/**
 * Repository/integration tests for the Phase 2 entity mappings and queries.
 *
 * <p>These run against the in-memory H2 database (PostgreSQL compatibility mode,
 * see application-test.yml) because Docker/PostgreSQL is not available on this
 * machine. They verify the entity relationships and the derived repository
 * queries. Real-PostgreSQL constraint/index checks are covered by the Flyway
 * migration (V1__init.sql) which is runnable once a PostgreSQL instance exists.
 */
@DataJpaTest
@ActiveProfiles("test")
class RepositoryIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Test
    void userUrlAndClickEventRelationshipsPersist() {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPassword("hashed-password");
        userRepository.save(user);

        Url url = new Url();
        url.setShortCode("abc123");
        url.setOriginalUrl("https://example.com/very/long/path");
        user.addUrl(url);
        urlRepository.save(url);

        ClickEvent click = new ClickEvent();
        click.setShortCode("abc123");
        click.setReferrer("https://ref.example.com");
        click.setUserAgent("Mozilla/5.0");
        click.setIp("203.0.113.7");
        click.setCountry("US");
        url.addClickEvent(click);
        clickEventRepository.save(click);

        // --- User -> Url (one-to-many) ---
        User fetchedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(fetchedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(fetchedUser.getUrls()).extracting(Url::getShortCode).containsExactly("abc123");

        // --- Url findByShortCode (redirect lookup) ---
        Optional<Url> byCode = urlRepository.findByShortCode("abc123");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
        assertThat(byCode.get().getUser().getId()).isEqualTo(user.getId());

        // --- Urls by user (paginated) ---
        Page<Url> userUrls = urlRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, 20));
        assertThat(userUrls.getContent()).hasSize(1)
                .extracting(Url::getShortCode).containsExactly("abc123");
        assertThat(userUrls.getTotalElements()).isEqualTo(1);

        // --- Ownership-aware lookup ---
        assertThat(urlRepository.findByIdAndUserId(url.getId(), user.getId())).hasValueSatisfying(u ->
                u.getShortCode().equals("abc123"));
        // A different (non-owning) user id must not see the row.
        assertThat(urlRepository.findByIdAndUserId(url.getId(), 999L)).isEmpty();

        // --- Url -> ClickEvent (one-to-many) ---
        assertThat(byCode.get().getClickEvents()).extracting(ClickEvent::getCountry).containsExactly("US");

        // --- Click events by URL ---
        List<ClickEvent> clicks = clickEventRepository.findByUrlId(url.getId());
        assertThat(clicks).hasSize(1);
        assertThat(clicks.get(0).getIp()).isEqualTo("203.0.113.7");
        assertThat(clicks.get(0).getUrl().getId()).isEqualTo(url.getId());
    }

    @Test
    void userEmailIsUnique() {
        User a = new User();
        a.setEmail("dup@example.com");
        a.setPassword("x");
        userRepository.save(a);

        User b = new User();
        b.setEmail("dup@example.com");
        b.setPassword("y");

        // H2 enforces the unique constraint from the entity mapping (users.email).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userRepository.saveAndFlush(b))
            .isInstanceOf(Exception.class);
    }

    @Test
    void findByEmailReturnsUser() {
        User user = new User();
        user.setEmail("bob@example.com");
        user.setPassword("hash");
        userRepository.save(user);

        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void shortCodesAreUnique() {
        Url first = new Url();
        first.setShortCode("unique01");
        first.setOriginalUrl("https://a.example");
        urlRepository.save(first);

        Url duplicate = new Url();
        duplicate.setShortCode("unique01");
        duplicate.setOriginalUrl("https://b.example");

        // H2 enforces the unique constraint from the entity mapping (urls.short_code).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> urlRepository.saveAndFlush(duplicate))
            .isInstanceOf(Exception.class);
    }
}