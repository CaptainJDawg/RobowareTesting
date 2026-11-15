# ISE-CS4431-Robocode

This program will be used to run engaging Robocode competitions. Flexible competition formats are feasibly configured and ran all from one user's machine. The program will take in entrant submission and competition format configuration as input. It leverages the classic robocode engine to run games and record the results to define clear winners.

## How to run

### macOS / Linux

```
./gradlew bootRun
```

### Windows

Run these commands in PowerShell from the project root. Keep the application running and use a second terminal for the API commands.

| Action | Command |
| --- | --- |
| Start the Spring Boot application | `.\gradlew.bat bootRun` |
| Start a battle | `curl.exe -X POST "http://localhost:8080/api/battle/start"` |
| View the current leaderboard | `curl.exe "http://localhost:8080/api/leaderboard"` |
| Stop the application | Press `Ctrl+C` in the terminal running the application. |

## Project Structure

```text
ISE-CS4431-Robocode/
├── README.md
├── rules.md
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── .gitignore
├── .gitattributes
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/robo_board/robot_board/
    │   │       └── RobotBoardApplication.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/
            └── com/robo_board/robot_board/
                └── RobotBoardApplicationTests.java
```


