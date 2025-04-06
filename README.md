justjournal
===========

JustJournal.com's Source

Just Journal is a blogging web application that supports multiple users and has clients in
C#, Java, Apple's Dashboard (JavaScript), and Unix command line (C)

JustJournal is developed on JDK 17 on Linux
using MySQL 8.0 and Spring Boot 3.1.x.  It has been successfully
deployed on MidnightBSD 3.2 with the OpenJDK 17 using MySQL 8.0.39.

It also uses minio for avatar storage and redis 7.x

If you would like more information, please look at the INSTALL file.

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
