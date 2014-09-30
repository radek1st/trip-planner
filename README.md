Trip Planner

Radek Ostrowski - dest.hawaii@gmail.com

Description:

Back-end built with Play Framework 2 and Scala, backed up by MongoDB with ReactiveMongo plugin.
Front-end built with Angular.js as a one-page application.

Database setup:

- download and run MongoDB (e.g. cd mongodb-osx-x86_64-2.6.4; bin/mongod)
- setup mongo by starting bin/mongo (shell) and run commands:
-- use trip-planner
-- db.createCollection("accounts")
(password needs to be hashed with HashingService: superSecret -> $2a$10$.ChY88hfPUYr8uQUCEVvNOIGNtZLXbZCzz9mpA6XRRs2hYAwh7pCS)
-- db.accounts.insert({ "email": "admin@tripplanner.com", "role": "admin", "password": "$2a$10$.ChY88hfPUYr8uQUCEVvNOIGNtZLXbZCzz9mpA6XRRs2hYAwh7pCS" })
-- db.accounts.ensureIndex({ email: 1 }, { unique: true })

To start the service:

- navigate into the main folder and start the app by specifying an open port (e.g. 9000):

./activator run -Dhttp.port=disabled -Dhttps.port=9000
(to debug in eclipse start it with: -jvm-debug 9999)

To use it:
- navigate your browser to https://localhost:9000


To check how the REST api works directly:
- look at the automated tests suite in test/controllers
