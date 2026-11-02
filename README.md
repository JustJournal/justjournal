justjournal
===========

JustJournal.com's Source

Just Journal is a blogging web application that supports multiple users and has clients in
C#, Java, Apple's Dashboard (JavaScript), and Unix command line (C)

JustJournal is developed on JDK 21 on Linux
using MySQL 8.0 and Spring Boot 3.3.x.  It has been successfully
deployed on MidnightBSD 4.0 with the OpenJDK 21 using MySQL 8.0.39.

It also uses minio for avatar storage and redis 7.x

If you would like more information, please look at the INSTALL file.

## Link Safety

Links attached to blog posts are checked against Spamhaus DBL and Google Safe Browsing before they
are saved. Both are set under `link-safety` in `application.yml`.

### Spamhaus DBL

DBL is on in the default profile (`link-safety.dbl.enabled: true`). Spamhaus refuses queries sent
through public DNS resolvers such as 8.8.8.8 or 1.1.1.1; those come back as "unknown" and the link
is allowed. To make the check work, do one of:

- Run a local recursive resolver (unbound, for example) and point the server's DNS at it, or
- Sign up for a free Spamhaus Data Query Service key at
  https://www.spamhaus.com/free-trial/sign-up-for-a-free-data-query-service-account/ and set the
  zone to your key:

  ```yaml
  link-safety:
    dbl:
      zone: <your-dqs-key>.dbl.dq.spamhaus.net
  ```

### Google Safe Browsing

Safe Browsing is off until an API key is set.

1. In the Google Cloud console, enable the Safe Browsing API:
   https://console.cloud.google.com/apis/library/safebrowsing.googleapis.com
   (see https://developers.google.com/safe-browsing/v4/get-started).
2. Create an API key under APIs & Services > Credentials and restrict it to the Safe Browsing API.
3. Give the key to JustJournal with the `SAFE_BROWSING_API_KEY` environment variable, or set
   `link-safety.safe-browsing.api-key` directly.

The Safe Browsing Lookup API is for non-commercial use and sends the checked URLs to Google.

Set `link-safety.fail-closed: true` to refuse a link when either service can't be reached.

## Breaking Changes

### 3.1.10
JJ no longer supports the legacy Blogger XML-RPC API.  The library used for this was not maintained and it wasn't compatible with modern jakarta servlets. 

Support for RTF exports was also dropped due to a licensing change from the library developer we used. PDF is supported. 

JJ now requires Java 17 due to the Spring Boot upgrade.

## Build Status
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=laffer1_justjournal&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=laffer1_justjournal)

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Flaffer1%2Fjustjournal.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Flaffer1%2Fjustjournal?ref=badge_shield)


## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Flaffer1%2Fjustjournal.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Flaffer1%2Fjustjournal?ref=badge_large)
