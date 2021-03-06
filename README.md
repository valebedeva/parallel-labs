# Parallel labs for course "Distributed and parallel programs"
## [Laba 4] Akka application with akka http designed for remote testing of JS applications.

State: Done

Compile and start:
```
cd laba4
mvn compile
mvn exec:java -Dexec.mainClass="laba4.JsScriptTester"
```
Example:
```
curl -H "Content-type: application/json" -X POST -d '{"packageId":"13", "jsScript":"var divideFn = function(a,b) { return a/b} ", "functionName":"divideFn", "tests": [{"testName":"test1", "expectedResult":"2.0", "params":[2,1]}, {"testName":"test2", "expectedResult":"2.0", "params":[5,2]}]}' http://localhost:8082/test/execute
```
Output: Executed
```
curl -X GET localhost:8082/test/result/13
```
Output:
[{"responseMsg":"OK","testName":"test1"},{"responseMsg":"test2: Expected: 2.0, but received: 2.5","testName":"test2"}]

## [Laba 5] Akka sream App for load testing

State: Done

Compile and start:
```
cd laba5
mvn compile
mvn exec:java -Dexec.mainClass="laba5.TimeRequestTester"
```
Example:
```
curl http://localhost:8080/?testUrl=http://rambler.ru&count=20
```
Output: time, ms