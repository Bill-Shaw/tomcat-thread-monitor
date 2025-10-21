# Changelog

All notable changes to the Tomcat Thread Monitor project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-10-21

### Added
- JSON API endpoint (`?action=json`) for integration with monitoring systems
  - Structured JSON response with thread metrics
  - Includes configured threshold values
  - Ideal for Prometheus, Grafana, Zabbix integration
- Configurable alert thresholds via web.xml
  - `warningThreshold` parameter (default: 60%)
  - `criticalThreshold` parameter (default: 80%)
  - Dynamic color-coded status based on custom thresholds
- Automatic log file rotation
  - `maxLogFileSize` parameter (default: 10MB)
  - `maxLogFiles` parameter (default: 10 files)
  - Automatic rotation when size limit exceeded
  - Old files automatically deleted when limit reached
- Maven build support (pom.xml)
  - Standard Maven project structure
  - Easy dependency management
  - Simplified build process
- Enhanced error handling
  - Specific exception types (JMException, IOException)
  - Detailed error messages for different failure scenarios
  - Better JMX error diagnostics

### Changed
- Improved exception handling throughout the codebase
  - Separate catch blocks for different exception types
  - More informative error messages
  - Better error logging for troubleshooting
- Enhanced input validation
  - Refresh interval bounds checking (5-3600 seconds)
  - Configuration parameter validation
  - Safe defaults for invalid values
- Refactored data formatting
  - Extracted CSV formatting to dedicated methods
  - Reusable formatting functions
  - Consistent data output across endpoints
- Updated build instructions in README
  - Maven build as recommended method
  - Updated manual build instructions
  - Added deployment steps

### Fixed
- Removed debug HTML comments from production output
- Fixed potential XSS vulnerabilities with HTML/JSON escaping
- Improved JMX connector discovery error handling
- Better handling of missing AJP connector

### Security
- Added HTML escaping for all user-facing output
- Added JSON escaping for JSON responses
- Input validation for all configuration parameters

## [1.0.0] - 2025-10-20

### Added
- Initial release
- Real-time thread monitoring dashboard
- HTTP and AJP connector metrics
- System thread statistics
- CSV export functionality
- File logging with daily rotation
- Auto-refresh capability
- Responsive web design
- Color-coded status indicators
- Zero external dependencies

[1.1.0]: https://github.com/Bill-Shaw/tomcat-thread-monitor/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Bill-Shaw/tomcat-thread-monitor/releases/tag/v1.0.0
