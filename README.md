# A Blocking Pinboard Client


![CI](https://github.com/joshlong/pinboard-client/workflows/CI/badge.svg)

## A Pinboard Client for non-Reactive, Traditional Spring MVC Applications

The `pinboard.resttemplate.RestTemplatePinboardClient` is a client based on the Spring `RestTemplate`. This uses traditional blocking I/O, based on `InputStream` and `OutputStream`. This might be your only choice in more traditional, Spring MVC, or Servlet-based environments.  

``` 
String myPinboardAccessToken = "...";
new pinboard.resttemplate.RestTemplatePinboardClient(myPinboardAccessToken);
``` 
