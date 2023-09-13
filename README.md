## A tool that enables importing BDD test results into ALM Octane

This tool parses the JUnit-style XML report generated by various BDD frameworks, crosschecking
the relevant .feature files in Gherkin syntax, 
and sends the result to ALM Octane via the 
[OpenText Application Automation Tools](https://plugins.jenkins.io/hp-application-automation-tools-plugin/).
This tool is configured as a build step before the "ALM Octane Cucumber test reporter". 

The following frameworks are currently supported out-of-the-box: 
- cucumber-js
  > verfied versions: 4.2.1, 5.1.0, 6.0.5, 7.3.0
- php-behat
  > verified versions: 3.8.1
- python-radish
  > verified versions: 0.13.2, 0.13.4 
- python-behave
  > verified versions: 1.2.5, 1.2.6
- cucumber-jvm
  > verifed versions: 2.0.0 - 7.0.0  
  > unsupported version: 1.2.6
- cucumber-ruby
  > verified versions: 4.0.0, 5.0.0, 6.0.0, 7.0.0

You can add your own framework by implementing the interface: [BddFrameworkHandler](./src/main/java/com/microfocus/bdd/api/BddFrameworkHandler.java)
SpecFlow test result injection is supported via the use of a template file for report generation. See https://marketplace.microfocus.com/appdelivery/content/alm-octane-bdd-automation-with-specflow.

### There are two ways to invoke this tool:
 1. Run as an executable-jar
    
    The CI admin downloads the .jar file and deploys it to the CI server nodes,
    then creates a job to invoke the following command line: 
     
    >**java -jar <path_to_the_jar> --reportFiles=<path_or_pattern> --featureFiles=<path_or_pattern> --framework=\<framework> --resultFile=\<path_to_result_file>**
    
    or a short version:
    > **java -jar <path_to_the_jar> -rf=<path_or_pattern> -ff=<path_or_pattern> -f=\<framework> -r=\<path_to_result_file>**
    
    The --resultFile is optional. If not provided, the default result file is <*framework*>-result.xml. 
     
    The following picture is a sample configuration of Jenkins in a Windows environment. As you can see, the second action consumes the result
    file from the first action, cucumber-jvm-result.xml:
    ![Run as jar](./run_as_jar.png)

 2. Run as a Maven plugin

    This tool is published in the Maven repository as a plugin. You can invoke the plugin by using a fully qualified plugin ID
    or a short version. Unlike the previous way, the CI admin doesn't need to manually deploy the tool.

    > **mvn bdd2octane:run -DreportFiles=<path_or_pattern> -DfeatureFiles=<path_or_pattern> -Dframework=\<framework> -DresultFile=<path_to_result_file>**

    The following picture is a sample configuration of invoking a Maven target to run the plugin.
    ![Run as mvn](./run_as_mvn.png)

### Notes on Cucumber-js handler

* Cucumber-js using the Gherkin library to parse the feature file. For Cucumber-js version 6.x and below, it uses the Gherkin
  library in version 5 or below which does not support the keyword "Example". 
  If a feature file contains "Example" as keyword, the program will throw an error.
  It is recommended to use "Scenario" instead of "Example" as the scenario keyword to avoid this error.
* "Example" keyword was added to the Gherkin library in version 6.0.13 on 9/25
  2018 [Gherkin library changelog](https://github.com/cucumber/common/blob/main/gherkin/CHANGELOG.md#6013---2018-09-25).
  Cucumber-js version 7 and above applies the updated Gherkin library and won't have this problem.

### Notes on Cucumber-jvm handler
* If **useFileNameCompatibleName** is enabled, the space character in a feature name is replaced with underscore(_) in JUnit report,
  currently the handler doesn't support this configuration.
    
* Since Maven Surefire 2.19.1, the JUnit report doesn't include failing call stack in \<failure> element or \<error> element. 
It is required to enable the "pretty" option of Cucumber-jvm. This tool will parse the <system-out> tag instead.
  
* There is a bug in Surefire if the feature name includes parentheses, the JUnit report is mangled. [SUREFIRE-1952](https://issues.apache.org/jira/browse/SUREFIRE-1952)

* If **stepNotifications** is enabled, feature name and scenario name will be replaced by scenario name and step name in JUnit report,
  currently the handler doesn't support this configuration.
