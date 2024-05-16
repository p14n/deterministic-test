A project to demonstrate testing events handlers in a pseudo-random, repeatable execution environment.

### Running the tests
```bash
clojure -Mkaocha --watch --no-capture-output

FAIL in core-test/run-handlers-in-random-order-spec (core_test.clj:109)
expected: {:result true}
  actual: {:shrunk {:total-nodes-visited 36, :depth 5, :pass? false, :result false, :result-data nil, :time-shrinking-ms 6, :smallest [false true -511 -2]}, :failed-after-ms 13, :num-tests 17, :seed 1715809825215, :fail [true true -731 -2], :result false, :result-data nil, :failing-size 16, :pass? false, :test-var "run-handlers-in-random-order-spec"}
2 tests, 2 assertions, 1 failures.

Ran 2 tests containing 2 assertions.
1 failures, 0 errors.
```

### Repeating the run in the repl
```bash
user=> (load-file "test/core_test.clj")
#'core-test/run-handlers-in-random-order-spec

user=> (clojure.pprint/pprint (core-test/run-handlers-in-random-order true true -731 -2))
{:pass false,
 :expected-email-status "blocked",
 :actual-email-status "active",
 :final-processed-events
 [{:type :create-customer-requested,
   :customer-id "123",
   :email "nobody@nowhere.com"}
  {:type :email-blacklist-completed,
   :customer-id "123",
   :blacklisted true}
  {:type :email-verification-sent, :customer-id "123"}
  {:type :email-verification-completed,
   :customer-id "123",
   :verified true}
  {:type :email-blacklist-sent, :customer-id "123"}]}
```
