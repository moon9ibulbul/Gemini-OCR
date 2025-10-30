@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
    set "PROPERTIES_FILE=%APP_HOME%\gradle\wrapper\gradle-wrapper.properties"
    if not exist "%PROPERTIES_FILE%" (
        echo ERROR: Gradle wrapper properties not found at %PROPERTIES_FILE% 1>&2
        goto fail
    )

    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$propertiesPath = '%PROPERTIES_FILE%';" ^
        "$lines = Get-Content -Path $propertiesPath;" ^
        "$distributionLine = $lines | Where-Object { $_ -like 'distributionUrl=*' } | Select-Object -First 1;" ^
        "if (-not $distributionLine) { Write-Error 'Unable to locate distributionUrl in gradle-wrapper.properties.'; exit 1 }" ^
        "$distributionUrl = $distributionLine.Split('=')[1].Replace('\', '');" ^
        "$versionMatch = [Regex]::Match($distributionUrl, 'gradle-([0-9.]+)-');" ^
        "if (-not $versionMatch.Success) { Write-Error \"Unable to parse Gradle version from $distributionUrl\"; exit 1 }" ^
        "$jarUrl = \"https://services.gradle.org/distributions/gradle-$($versionMatch.Groups[1].Value)-wrapper.jar\";" ^
        "Write-Host \"Downloading Gradle wrapper JAR from $jarUrl\";" ^
        "Invoke-WebRequest -Uri $jarUrl -OutFile '%WRAPPER_JAR%' -UseBasicParsing"

    if %ERRORLEVEL% neq 0 goto fail
    if not exist "%WRAPPER_JAR%" goto fail
)

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
