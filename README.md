# Tomcat Thread Monitor
A lightweight web application for real-time monitoring of Apache Tomcat thread pool usage. Deploy as a WAR file to get instant visibility into your Tomcat server's thread health.

## Features
- **Real-time Monitoring**: Auto-refreshing dashboard with configurable intervals (5-120 seconds)
- **Thread Pool Metrics**: HTTP and AJP connector thread usage and availability
- **System Thread Stats**: JVM-wide thread counts (total, peak, daemon)
- **Visual Indicators**: Color-coded status (green/yellow/red) based on utilization
- **CSV Export**: Download current thread data for analysis
- **File Logging**: Automatic logging to daily CSV files on server
- **Responsive Design**: Works on desktop and mobile devices
- **Zero Dependencies**: Uses only standard Java APIs

## Quick Start

### Option 1: Download Pre-built WAR
1. Download the latest `thread-monitor.war` from [Releases](https://github.com/Bill-Shaw/tomcat-thread-monitor/releases)
2. Copy to your Tomcat `webapps` directory: `cp thread-monitor.war $CATALINA_HOME/webapps/`
3. Access at `http://localhost:8080/thread-monitor`

### Option 2: Build from Source
```bash
/u01/java/jdk11/bin/javac -cp "/u01/apps/tomcat/lib/servlet-api.jar" -d /u01/threadmonitor/build/WEB-INF/classes /u01/threadmonitor/src/main/java/com/monitor/threads/ThreadMonitorServlet.java
cd /u01/threadmonitor/build
/u01/java/jdk11/bin/jar -cvf ../thread-monitor.war *
cd ..
```

## Usage
### Web Interface
- **Dashboard**: `http://localhost:8080/thread-monitor`
- **Export CSV**: Click "Export CSV" button or visit `http://localhost:8080/thread-monitor?action=export`
- **Log to File**: Click "Log to File" button or visit `http://localhost:8080/thread-monitor?action=log`

### Automated Logging
Create a new shell script and aSet up automatic data collection every 5 minutes:

```bash
touch threads.sh
chmod +x threads.sh
vi threads.sh

#!/bin/bash
cd /tmp
wget http://localhost:8080/thread-monitor?action=log
rm -rf thread-monitor?action=log*

# Add to crontab
*/5 * * * * curl -s "http://localhost:8080/thread-monitor?action=log" > /dev/null
```

### API Endpoints
- `GET /thread-monitor` - Main dashboard (HTML)
- `GET /thread-monitor?action=export` - Download CSV data
- `GET /thread-monitor?action=log` - Log data to server file (JSON response)

## Configuration

### Log Directory
Configure where CSV files are stored by editing `src/main/webapp/WEB-INF/web.xml`:

```xml
<context-param>
    <param-name>logDirectory</param-name>
    <param-value>/path/to/your/logs</param-value>
</context-param>
```

Default location: `$CATALINA_HOME/logs/thread-monitor/`

### Security (Optional)
Restrict access by uncommenting the security constraints in `web.xml` and adding users to `tomcat-users.xml`.

## Monitoring Metrics

| Metric | Description |
|--------|-------------|
| **HTTP Connector Threads** | Active vs. maximum configured threads for HTTP |
| **AJP Connector Threads** | Active vs. maximum configured threads for AJP (if configured) |
| **Thread Utilization** | Percentage-based utilization with color coding |
| **System Thread Count** | JVM-wide thread statistics |
| **Available Threads** | Remaining thread capacity |

## CSV Data Format

Generated CSV files include:
- `Timestamp` - When the data was captured
- `HTTP_Busy_Threads` - Number of busy HTTP connector threads
- `HTTP_Max_Threads` - Maximum HTTP connector threads configured
- `HTTP_Available` - Available HTTP threads (max - busy)
- `HTTP_Utilization_Percent` - HTTP thread pool utilization percentage
- `AJP_*` - AJP connector metrics (if configured)
- `Total_System_Threads` - Total JVM threads
- `Peak_System_Threads` - Peak JVM thread count
- `Daemon_Threads` - Number of daemon threads

## Requirements

- **Java**: 8 or higher
- **Tomcat**: 8.5 or higher
- **Servlet API**: 3.1 or higher
- **Build Tool**: Maven 3.6+ (for building from source)

## Building

```bash
# Build WAR file
mvn clean package

# Run tests
mvn test

# Build with specific Java version
mvn clean package -Djava.version=11
```

## Troubleshooting

### Common Issues

**WAR deployment fails:**
- Check Tomcat logs: `tail -f $CATALINA_HOME/logs/catalina.out`
- Verify Java version compatibility

**File logging not working:**
- Check directory permissions: `ls -la $CATALINA_HOME/logs/`
- Ensure Tomcat user can write to log directory
- Check for disk space

**Missing thread data:**
- Ensure application runs in same JVM as Tomcat
- Verify connector configuration in `server.xml`
- Check JMX permissions

**curl vs browser differences:**
```bash
# Test with verbose output
curl -v "http://localhost:8080/thread-monitor?action=log"
```

### Performance Impact

- **Minimal overhead**: Read-only JMX operations
- **No persistent storage**: All data processing is real-time
- **Configurable refresh**: Adjust monitoring frequency as needed
- **Lightweight requests**: Small HTTP payload

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## AI-Generated Code Notice

This project was developed with assistance from **Claude Sonnet 4** (Anthropic). The codebase, documentation, and project structure were generated through collaborative AI-assisted development.

### What was AI-generated:
- Core servlet implementation and thread monitoring logic
- Web interface HTML/CSS/JavaScript
- Maven build configuration and project structure (not tested)
- Documentation and README content
- Unit tests foundation
- GitHub Actions CI/CD workflow

### Human contributions:
- Project requirements and specifications
- Testing and validation
- Deployment and configuration decisions
- Repository setup and maintenance

This transparency notice is provided to acknowledge the use of generative AI in software development and to comply with best practices for AI-assisted code creation.

## Support

- **Issues**: [GitHub Issues](https://github.com/Bill-Shaw/tomcat-thread-monitor/issues)
- **Documentation**: Check this README and inline code comments
- **Community**: [GitHub Discussions](https://github.com/Bill-Shaw/tomcat-thread-monitor/discussions)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and updates.

## Acknowledgments

- **Claude Sonnet 4** by Anthropic for AI-assisted development
- Apache Tomcat community for excellent documentation
- JMX specification for providing thread monitoring capabilities

---

**Author**: [Bill Shaw](https://github.com/Bill-Shaw)  
**AI Assistant**: Claude Sonnet 4 (Anthropic)  
**Repository**: https://github.com/Bill-Shaw/tomcat-thread-monitor
