@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  lenskit-hello-master startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Add default JVM options here. You can also use JAVA_OPTS and LENSKIT_HELLO_MASTER_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%$

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\lenskit-hello-master.jar;%APP_HOME%\lib\lenskit-all-3.0-M1.jar;%APP_HOME%\lib\logback-classic-1.1.2.jar;%APP_HOME%\lib\slf4j-api-1.7.12.jar;%APP_HOME%\lib\annotations-3.0.1u2.jar;%APP_HOME%\lib\lenskit-core-3.0-M1.jar;%APP_HOME%\lib\lenskit-eval-3.0-M1.jar;%APP_HOME%\lib\lenskit-knn-3.0-M1.jar;%APP_HOME%\lib\lenskit-svd-3.0-M1.jar;%APP_HOME%\lib\lenskit-predict-3.0-M1.jar;%APP_HOME%\lib\lenskit-slopeone-3.0-M1.jar;%APP_HOME%\lib\logback-core-1.1.2.jar;%APP_HOME%\lib\jcip-annotations-1.0.jar;%APP_HOME%\lib\jsr305-3.0.1.jar;%APP_HOME%\lib\lenskit-api-3.0-M1.jar;%APP_HOME%\lib\fastutil-7.0.12.jar;%APP_HOME%\lib\grapht-0.11.0-BETA2.jar;%APP_HOME%\lib\guava-19.0.jar;%APP_HOME%\lib\joda-convert-1.8.1.jar;%APP_HOME%\lib\jackson-databind-2.7.4.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.7.4.jar;%APP_HOME%\lib\commons-lang3-3.4.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\commons-compress-1.12.jar;%APP_HOME%\lib\xz-1.5.jar;%APP_HOME%\lib\lenskit-groovy-3.0-M1.jar;%APP_HOME%\lib\joda-time-2.3.jar;%APP_HOME%\lib\ant-1.8.4.jar;%APP_HOME%\lib\hamcrest-library-1.3.jar;%APP_HOME%\lib\javax.inject-1.jar;%APP_HOME%\lib\jackson-annotations-2.7.0.jar;%APP_HOME%\lib\jackson-core-2.7.4.jar;%APP_HOME%\lib\snakeyaml-1.15.jar;%APP_HOME%\lib\groovy-all-2.4.4.jar;%APP_HOME%\lib\ant-launcher-1.8.4.jar;%APP_HOME%\lib\hamcrest-core-1.3.jar

@rem Execute lenskit-hello-master
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %LENSKIT_HELLO_MASTER_OPTS%  -classpath "%CLASSPATH%" org.grouplens.lenskit.hello.HelloLenskit %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable LENSKIT_HELLO_MASTER_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%LENSKIT_HELLO_MASTER_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
