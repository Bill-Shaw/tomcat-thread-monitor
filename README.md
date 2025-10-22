# Tomcat Thread Monitor
A lightweight web application for real-time monitoring of Apache Tomcat thread pool usage. Deploy as a WAR file to get instant visibility into your Tomcat server's thread health.

## Features
- **Real-time Monitoring**: Auto-refreshing dashboard with configurable intervals (5-3600 seconds)
- **Thread Pool Metrics**: HTTP and AJP connector thread usage and availability
- **System Thread Stats**: JVM-wide thread counts (total, peak, daemon)
- **Visual Indicators**: Color-coded status (green/yellow/red) based on utilization
- **Configurable Alert Thresholds**: Customize warning and critical levels via web.xml
- **CSV Export**: Download current thread data for analysis
- **JSON API**: RESTful JSON endpoint for integration with monitoring systems
- **File Logging**: Automatic logging to daily CSV files on server
- **Log Rotation**: Automatic log file rotation based on configurable size limits
- **Enhanced Error Handling**: Specific exception handling with detailed logging
- **Responsive Design**: Works on desktop and mobile devices
- **Zero Dependencies**: Uses only standard Java APIs

## Quick Start
### Option 1: Download Pre-built WAR
1. Download the latest `thread-monitor.war` from [Releases](https://github.com/Bill-Shaw/tomcat-thread-monitor/releases)
2. Copy to your Tomcat `webapps` directory: `cp thread-monitor.war $CATALINA_HOME/webapps/`
3. Access at `http://localhost:8080/thread-monitor`

### Option 2: Build from Source

**Using Maven (Recommended):**
```bash
# Clone the repository
git clone https://github.com/Bill-Shaw/tomcat-thread-monitor.git
cd tomcat-thread-monitor

# Build with Maven
mvn clean package

# Deploy the WAR file
cp target/thread-monitor.war $CATALINA_HOME/webapps/
```

**Using manual build:**
```bash
javac -cp "$CATALINA_HOME/lib/servlet-api.jar" -d build/WEB-INF/classes src/main/java/com/monitor/threads/ThreadMonitorServlet.java

cd build
jar -cvf ../thread-monitor.war *
cd ..

cp thread-monitor.war $CATALINA_HOME/webapps/
```

## Usage
### Web Interface
- **Dashboard**: `http://localhost:8080/thread-monitor`
- **Export CSV**: Click "Export CSV" button or visit `http://localhost:8080/thread-monitor?action=export`
- **Log to File**: Click "Log to File" button or visit `http://localhost:8080/thread-monitor?action=log`

### Automated Logging
Set up automatic data collection every 5 minutes:

```bash
# Add to crontab
*/5 * * * * curl -s "http://localhost:8080/thread-monitor?action=log" > /dev/null
```

### API Endpoints
- `GET /thread-monitor` - Main dashboard (HTML)
- `GET /thread-monitor?action=export` - Download CSV data
- `GET /thread-monitor?action=json` - Get thread metrics as JSON (for monitoring integrations)
- `GET /thread-monitor?action=log` - Log data to server file (JSON response)

## Configuration

All configuration is done via `src/main/webapp/WEB-INF/web.xml` context parameters:

### Available Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `logDirectory` | `${catalina.home}/logs/thread-monitor` | Directory where CSV log files are stored |
| `warningThreshold` | `60` | Thread utilization percentage (0-100) for warning status |
| `criticalThreshold` | `80` | Thread utilization percentage (0-100) for critical status |
| `maxLogFileSize` | `10485760` | Maximum log file size in bytes before rotation (10MB) |
| `maxLogFiles` | `10` | Maximum number of rotated log files to keep |

### Example Configuration

Edit `src/main/webapp/WEB-INF/web.xml`:

```xml
<!-- Custom log directory -->
<context-param>
    <param-name>logDirectory</param-name>
    <param-value>/var/log/tomcat-monitor</param-value>
</context-param>

<!-- Custom alert thresholds -->
<context-param>
    <param-name>warningThreshold</param-name>
    <param-value>70</param-value>
</context-param>

<context-param>
    <param-name>criticalThreshold</param-name>
    <param-value>90</param-value>
</context-param>

<!-- Log rotation settings -->
<context-param>
    <param-name>maxLogFileSize</param-name>
    <param-value>5242880</param-value> <!-- 5MB -->
</context-param>

<context-param>
    <param-name>maxLogFiles</param-name>
    <param-value>20</param-value>
</context-param>
```

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

## JSON API Response Format

The JSON endpoint (`?action=json`) returns data in the following format:

```json
{
  "timestamp": "2025-10-21T10:30:00Z",
  "http": {
    "busyThreads": 15,
    "maxThreads": 200,
    "availableThreads": 185,
    "utilizationPercent": 7.50
  },
  "ajp": {
    "busyThreads": 0,
    "maxThreads": 0,
    "availableThreads": 0,
    "utilizationPercent": 0.00
  },
  "system": {
    "totalThreads": 42,
    "peakThreads": 58,
    "daemonThreads": 38,
    "nonDaemonThreads": 4
  },
  "thresholds": {
    "warningPercent": 60,
    "criticalPercent": 80
  }
}
```

This format is ideal for integration with monitoring tools like Prometheus, Grafana, Zabbix, or custom monitoring scripts.

## Requirements

- **Java**: 8 or higher (war file compiled with JDK21)
- **Tomcat**: 8.5 or higher
- **Servlet API**: 3.1 or higher
- **Build Tool**: Maven 3.6+ (for building from source)

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
- Maven build configuration and project structure
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

### Version 1.1.0 (Latest)

**New Features:**
- JSON API endpoint for integration with monitoring systems (`?action=json`)
- Configurable alert thresholds (warning and critical levels)
- Automatic log file rotation with configurable size limits
- Maven build support for easier project building
- Enhanced error handling with specific exception types

**Improvements:**
- Better exception handling with JMX-specific error messages
- Improved logging for troubleshooting
- Input validation for all configuration parameters
- HTML/JSON escaping for security
- Removed debug comments from production output

**Configuration:**
- Added `warningThreshold` parameter (default: 60%)
- Added `criticalThreshold` parameter (default: 80%)
- Added `maxLogFileSize` parameter (default: 10MB)
- Added `maxLogFiles` parameter (default: 10 files)

See [CHANGELOG.md](CHANGELOG.md) for complete version history.

## Acknowledgments

- **Claude Sonnet 4** by Anthropic for AI-assisted development
- Apache Tomcat community for excellent documentation
- JMX specification for providing thread monitoring capabilities

---

**Author**: [Bill Shaw](https://github.com/Bill-Shaw)  
**AI Assistant**: Claude Sonnet 4 (Anthropic)  
**Repository**: https://github.com/Bill-Shaw/tomcat-thread-monitor
