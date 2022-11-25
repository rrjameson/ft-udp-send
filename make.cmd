@echo off
setlocal
set JDK_HOME=%TLS_ROOT_PATH%\tls\jre
set MODULE=Server
pushd "%~dp0"
  set ROOT=%CD%  
  set CP=%ROOT%\lib\*
  
  if not exist out mkdir out
  if not exist out\production mkdir out\production
  if not exist out\artifacts mkdir out\artifacts  
  if exist out\production\%MODULE% rd /s /q out\production\%MODULE%
  if exist out\artifacts\%MODULE% rd /s /q out\artifacts\%MODULE%  
  mkdir out\production\%MODULE%
  mkdir out\artifacts\%MODULE%
    
  set COMP_ARGS=-Xdiags:verbose
  
  echo Getting source files
  dir /b /s %MODULE%\src\*.java>%MODULE%-sources.txt
  
  echo Compiling sources
  "%JDK_HOME%\bin\javac.exe" %COMP_ARGS% -classpath "%CP%" -d out\production\%MODULE% @%MODULE%-sources.txt
  
  echo Making jar
  "%JDK_HOME%\bin\jar.exe" -cf out\artifacts\%MODULE%\%MODULE%.jar -C out\production\%MODULE% .
popd
goto:EOF
