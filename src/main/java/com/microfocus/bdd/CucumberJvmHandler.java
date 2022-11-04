/*
 * © Copyright [2021] Micro Focus or one of its affiliates.
 * Licensed under Apache License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * http://www.apache.org/licenses/
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.microfocus.bdd;


import com.microfocus.bdd.api.*;

import java.util.Iterator;
import java.util.Optional;

public class CucumberJvmHandler implements BddFrameworkHandler {

    private Element element;
    private String errorMessage;
    private String failedStep;
    private String featureFile;
    private String failedLineNum;
    private boolean isSkipped = false;

    @Override
    public String getName() {
        return "cucumber-jvm";
    }

    @Override
    public void setElement(Element element) {
        this.element = element;
        if (!element.getChildren().isEmpty()) {
            Element child = element.getChildren().get(0);
            String childName = child.getName();
            if (childName.equals("error") && ("io.cucumber.junit.UndefinedThrowable".equals(child.getAttribute("type")) ||
                    "io.cucumber.junit.UndefinedStepException".equals(child.getAttribute("type")))) {
                /* sample error
                <error message="The step &quot;nothing happened&quot; is undefined" type="io.cucumber.junit.UndefinedThrowable">io.cucumber.junit.UndefinedThrowable: The step &quot;nothing happened&quot; is undefined</error>
                <error message="The step &apos;yet another action&apos; is undefined" type="io.cucumber.junit.UndefinedStepException">(for version io.cucumber 7.0.0)
                */

                errorMessage = child.getAttribute("message");
                int firstIndex = errorMessage.indexOf('\"');
                int lastIndex = errorMessage.lastIndexOf('\"');
                failedStep = errorMessage.substring(firstIndex + 1, lastIndex);
                return;
            }
            if (childName.equals("failure") || childName.equals("error")) {

                /* sample call stack
StackTrace:
java.lang.AssertionError
	at org.junit.Assert.fail(Assert.java:87)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at org.junit.Assert.assertTrue(Assert.java:53)
	at hellocucumber.failed.StepDefinitions.the_following_people_exist(StepDefinitions.java:12)
	at ✽.the following people exist:(file:///C:/junit2octane/src/test/resources/features/robustgherkin.feature:14)

                 */

                errorMessage = child.getText();
                String lastLine = findLastNonEmptyLine(errorMessage);
                if (lastLine.startsWith("at ✽.")) {
                    extractFeatureFilePath(lastLine);
                } else {
                    Optional<String> optionalString = findFirstStarLine(errorMessage);
                    if (optionalString.isPresent()) {
                        extractFeatureFilePath(optionalString.get());
                    } else {
                        element.getChild("system-out").ifPresent(out -> {
                            errorMessage = out.getText();
                            String failedLine = null;
                            int indexOfPeriod = 0;
                            for (String line : getLinesBottomUp(errorMessage)) {
                                if (line.startsWith("at ") && line.endsWith(")")) {
                                    indexOfPeriod = line.indexOf('.');
                                    if (indexOfPeriod != -1) {
                                        failedLine = line;
                                        break;
                                    }
                                }
                            }
                            if (failedLine == null) {
                                return;
                            }
                            int startOfFileLocation = failedLine.lastIndexOf("(");
                            failedStep = failedLine.substring(indexOfPeriod + 1, startOfFileLocation);
                            featureFile = failedLine.substring(startOfFileLocation + 1, failedLine.lastIndexOf(')'));
                            int lineNumIndex = featureFile.lastIndexOf(':');
                            failedLineNum = featureFile.substring(lineNumIndex + 1);
                            featureFile = featureFile.substring(0, lineNumIndex);
                        });
                    }
                }
            } else if (childName.equals("skipped")) {
                isSkipped = true;
            }
        }
    }

    private String findLastNonEmptyLine(String message) {
        for (String line : getLinesBottomUp(message)) {
            if (!line.isEmpty()) {
                return line;
            }
        }
        return null;
    }

    private Optional<String> findFirstStarLine(String message) {
        for (String line : getLines(message)) {
            if (line.startsWith("at ✽.")) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    private Iterable<String> getLinesBottomUp(String message) {
        return () -> new LinesBottomUpIterator(message);
    }

    private Iterable<String> getLines(String message) {
        return () -> new LinesIterator(message);
    }

    private void extractFeatureFilePath(String line) {
        int startOfFileLocation = line.lastIndexOf("(");
        failedStep = line.substring(5, startOfFileLocation);
        featureFile = line.substring(startOfFileLocation + 1, line.lastIndexOf(')'));
        int lineNumIndex = featureFile.lastIndexOf(':');
        failedLineNum = featureFile.substring(lineNumIndex + 1);
        featureFile = featureFile.substring(0, lineNumIndex);
    }

    @Override
    public Optional<String> getFeatureName() {
        String classname = element.getAttribute("classname");
        if (classname.isEmpty() || classname.equals("EMPTY_NAME") || classname.equals("Unknown")) {
            return Optional.empty();
        }
        return Optional.of(classname);
    }

    @Override
    public Optional<String> getFeatureFile() {
        return Optional.ofNullable(featureFile);
    }

    @Override
    public String getScenarioName(OctaneFeature feature) {
        String sceName = element.getAttribute("name");
        if (!feature.getScenarios(sceName).isEmpty()) {
            return sceName;
        }
        for (OctaneScenario sce : feature.getScenarios()) {
            if (sceName.startsWith(sce.getOutlineName())) {
                return (sce.getName());
            }
        }
        return null;
    }

/*
    @Override
    public TestcaseMeta getTestcaseMeta(OctaneFeature octaneFeature) {
        String sceName = element.getAttribute("name");
        TestcaseMeta testCase = new TestcaseMeta();
        if (octaneFeature.getScenarios().containsKey(sceName)) {
            testCase.setTestcaseName(sceName);
            return testCase;
        }
        for (OctaneScenario sce : octaneFeature.getScenarios().values()) {
            if ("Scenario Outline".equals(sce.getScenarioType())) {
                if (sce.getOutlineName().equals(sceName)) {
                    testCase.setTestcaseName(sce.getName());
                    testCase.setOutlineIndex(sce.getOutlineIndex());
                    return testCase;
                } else if (sceName.startsWith(sce.getOutlineName())) {
                    String index = sceName.substring(sce.getOutlineName().length());
                    int i = Integer.parseInt(index.trim());
                    testCase.setTestcaseName(sce.getName());
                    testCase.setOutlineIndex(i);
                    return testCase;
                }
            }
        }
        return null;
    }
*/

    @Override
    public void fillStep(OctaneStep octaneStep) {
        if (isSkipped) {
            octaneStep.setStatus(Status.SKIPPED);
            return;
        }
        if (failedStep == null) {
            octaneStep.setStatus(Status.PASSED);
            return;
        }
        if (octaneStep.getName().equals(failedStep)) {
            octaneStep.setStatus(Status.FAILED);
            octaneStep.setErrorMessage(errorMessage);
        } else {
            octaneStep.setStatus(Status.PASSED);
        }
    }

    @Override
    public String getTestCaseElementName() {
        return "testcase";
    }
}

