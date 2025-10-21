package com.monitor.threads;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.AttributeNotFoundException;
import javax.management.JMException;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebServlet("/thread-monitor")
public class ThreadMonitorServlet extends HttpServlet {

    private String logDirectory;
    private int warningThreshold = 60;  // Default 60%
    private int criticalThreshold = 80; // Default 80%
    private long maxLogFileSize = 10 * 1024 * 1024; // Default 10MB
    private int maxLogFiles = 10; // Default keep 10 files

    @Override
    public void init() throws ServletException {
        super.init();

        // Load log directory configuration
        logDirectory = getServletContext().getInitParameter("logDirectory");
        if (logDirectory == null || logDirectory.trim().isEmpty()) {
            logDirectory = System.getProperty("catalina.home", System.getProperty("user.home")) + "/logs/thread-monitor";
        }

        // Load alert threshold configurations
        String warningThresholdParam = getServletContext().getInitParameter("warningThreshold");
        if (warningThresholdParam != null && !warningThresholdParam.trim().isEmpty()) {
            try {
                warningThreshold = Integer.parseInt(warningThresholdParam);
                if (warningThreshold < 0 || warningThreshold > 100) {
                    log("Invalid warningThreshold value, using default: 60");
                    warningThreshold = 60;
                }
            } catch (NumberFormatException e) {
                log("Invalid warningThreshold format, using default: 60", e);
            }
        }

        String criticalThresholdParam = getServletContext().getInitParameter("criticalThreshold");
        if (criticalThresholdParam != null && !criticalThresholdParam.trim().isEmpty()) {
            try {
                criticalThreshold = Integer.parseInt(criticalThresholdParam);
                if (criticalThreshold < 0 || criticalThreshold > 100) {
                    log("Invalid criticalThreshold value, using default: 80");
                    criticalThreshold = 80;
                }
            } catch (NumberFormatException e) {
                log("Invalid criticalThreshold format, using default: 80", e);
            }
        }

        // Load log file rotation configurations
        String maxLogFileSizeParam = getServletContext().getInitParameter("maxLogFileSize");
        if (maxLogFileSizeParam != null && !maxLogFileSizeParam.trim().isEmpty()) {
            try {
                maxLogFileSize = Long.parseLong(maxLogFileSizeParam);
                if (maxLogFileSize < 1024) { // Minimum 1KB
                    log("maxLogFileSize too small, using default: 10MB");
                    maxLogFileSize = 10 * 1024 * 1024;
                }
            } catch (NumberFormatException e) {
                log("Invalid maxLogFileSize format, using default: 10MB", e);
            }
        }

        String maxLogFilesParam = getServletContext().getInitParameter("maxLogFiles");
        if (maxLogFilesParam != null && !maxLogFilesParam.trim().isEmpty()) {
            try {
                maxLogFiles = Integer.parseInt(maxLogFilesParam);
                if (maxLogFiles < 1) {
                    log("maxLogFiles too small, using default: 10");
                    maxLogFiles = 10;
                }
            } catch (NumberFormatException e) {
                log("Invalid maxLogFiles format, using default: 10", e);
            }
        }

        // Create log directory if it doesn't exist
        File logDir = new File(logDirectory);
        if (!logDir.exists()) {
            try {
                boolean created = logDir.mkdirs();
                if (!created) {
                    throw new ServletException("Failed to create log directory: " + logDir.getAbsolutePath());
                }
                log("Log directory created: " + logDir.getAbsolutePath());
            } catch (SecurityException e) {
                throw new ServletException("Permission denied creating log directory: " + logDir.getAbsolutePath(), e);
            }
        }

        log("Thread monitor initialized. Log directory: " + logDirectory +
            ", Warning threshold: " + warningThreshold + "%" +
            ", Critical threshold: " + criticalThreshold + "%" +
            ", Max log file size: " + (maxLogFileSize / 1024) + "KB" +
            ", Max log files: " + maxLogFiles);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        try {
            if ("export".equals(action)) {
                handleExport(request, response);
            } else if ("log".equals(action)) {
                handleLogToFile(request, response);
            } else if ("json".equals(action)) {
                handleJsonExport(request, response);
            } else {
                handleMonitorDisplay(request, response);
            }
        } catch (Exception e) {
            log("Error processing request: action=" + action, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "An error occurred while processing your request. Please check server logs for details.");
        }
    }
    
    private void handleExport(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"thread-monitor-" +
                          new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv\"");

        PrintWriter out = response.getWriter();

        try {
            ThreadInfo threadInfo = getThreadInformation();
            String csvData = formatThreadInfoAsCSV(threadInfo);
            out.print(csvData);

        } catch (JMException e) {
            log("JMX error during CSV export", e);
            out.println("Error,JMX data retrieval failed: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log("I/O error during CSV export", e);
            throw e;
        } catch (Exception e) {
            log("Unexpected error during CSV export", e);
            out.println("Error,Unexpected error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleJsonExport(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        PrintWriter out = response.getWriter();

        try {
            ThreadInfo threadInfo = getThreadInformation();
            String jsonData = formatThreadInfoAsJSON(threadInfo);
            out.print(jsonData);

        } catch (JMException e) {
            log("JMX error during JSON export", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"JMX data retrieval failed: " +
                     escapeJson(e.getMessage()) + "\"}");
        } catch (IOException e) {
            log("I/O error during JSON export", e);
            throw e;
        } catch (Exception e) {
            log("Unexpected error during JSON export", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Unexpected error: " +
                     escapeJson(e.getMessage()) + "\"}");
        }
    }
    
    private void handleLogToFile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            log("handleLogToFile called from: " + request.getRemoteAddr() +
                " User-Agent: " + request.getHeader("User-Agent"));

            ThreadInfo threadInfo = getThreadInformation();
            String logResult = logThreadDataToFile(threadInfo);

            log("Log result: " + logResult);

            out.println("{");
            out.println("  \"status\": \"success\",");
            out.println("  \"message\": \"" + escapeJson(logResult) + "\",");
            out.println("  \"timestamp\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\"");
            out.println("}");

            out.flush();
            response.flushBuffer();

        } catch (JMException e) {
            log("JMX error in handleLogToFile: " + e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("{");
            out.println("  \"status\": \"error\",");
            out.println("  \"message\": \"JMX data retrieval failed: " + escapeJson(e.getMessage()) + "\"");
            out.println("}");
            out.flush();
        } catch (IOException e) {
            log("I/O error in handleLogToFile: " + e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("{");
            out.println("  \"status\": \"error\",");
            out.println("  \"message\": \"File write failed: " + escapeJson(e.getMessage()) + "\"");
            out.println("}");
            out.flush();
        } catch (Exception e) {
            log("Unexpected error in handleLogToFile: " + e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("{");
            out.println("  \"status\": \"error\",");
            out.println("  \"message\": \"Unexpected error: " + escapeJson(e.getMessage()) + "\"");
            out.println("}");
            out.flush();
        }
    }
    
    private void handleMonitorDisplay(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        PrintWriter out = response.getWriter();

        try {
            String refreshParam = request.getParameter("refresh");
            int refreshInterval = 30; // default 30 seconds

            if (refreshParam != null && !refreshParam.isEmpty()) {
                try {
                    refreshInterval = Integer.parseInt(refreshParam);
                    if (refreshInterval < 5) refreshInterval = 5; // minimum 5 seconds
                    if (refreshInterval > 3600) refreshInterval = 3600; // maximum 1 hour
                } catch (NumberFormatException e) {
                    log("Invalid refresh interval: " + refreshParam + ", using default 30s");
                    refreshInterval = 30;
                }
            }
            
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Tomcat Thread Monitor</title>");
            out.println("<meta http-equiv=\"refresh\" content=\"" + refreshInterval + "\">");
            out.println("<style>");
            out.println("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }");
            out.println(".container { max-width: 1200px; margin: 0 auto; }");
            out.println(".header { background: #2c3e50; color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }");
            out.println(".metric-card { background: white; padding: 20px; margin: 10px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
            out.println(".metric-title { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; }");
            out.println(".metric-value { font-size: 24px; font-weight: bold; }");
            out.println(".normal { color: #27ae60; }");
            out.println(".warning { color: #f39c12; }");
            out.println(".critical { color: #e74c3c; }");
            out.println(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 15px; }");
            out.println(".controls { background: white; padding: 15px; border-radius: 8px; margin-bottom: 20px; }");
            out.println(".controls button { background: #3498db; color: white; padding: 8px 16px; border: none; border-radius: 4px; margin: 0 5px; cursor: pointer; }");
            out.println(".controls button:hover { background: #2980b9; }");
            out.println(".controls .success { background: #27ae60; }");
            out.println(".controls .error { background: #e74c3c; }");
            out.println(".thread-details { background: white; padding: 20px; border-radius: 8px; margin-top: 20px; }");
            out.println("table { width: 100%; border-collapse: collapse; margin-top: 10px; }");
            out.println("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }");
            out.println("th { background-color: #f8f9fa; font-weight: bold; }");
            out.println("#logStatus { margin-left: 10px; padding: 5px 10px; border-radius: 4px; font-size: 12px; }");
            out.println("</style>");
            out.println("<script>");
            out.println("function exportCSV() {");
            out.println("  window.location.href = '?action=export';");
            out.println("}");
            out.println("function logToFile() {");
            out.println("  fetch('?action=log')");
            out.println("    .then(response => response.json())");
            out.println("    .then(data => {");
            out.println("      const status = document.getElementById('logStatus');");
            out.println("      status.textContent = data.message;");
            out.println("      status.className = data.status === 'success' ? 'success' : 'error';");
            out.println("      setTimeout(() => { status.textContent = ''; status.className = ''; }, 5000);");
            out.println("    })");
            out.println("    .catch(err => {");
            out.println("      const status = document.getElementById('logStatus');");
            out.println("      status.textContent = 'Error: ' + err.message;");
            out.println("      status.className = 'error';");
            out.println("      setTimeout(() => { status.textContent = ''; status.className = ''; }, 5000);");
            out.println("    });");
            out.println("}");
            out.println("</script>");
            out.println("</head>");
            out.println("<body>");
            
            out.println("<div class=\"container\">");
            out.println("<div class=\"header\">");
            out.println("<h1>Tomcat Thread Monitor</h1>");
            out.println("<p>Real-time monitoring of Tomcat thread pools and system threads</p>");
            out.println("</div>");
            
            // Refresh controls
            out.println("<div class=\"controls\">");
            out.println("<form method=\"get\" style=\"display: inline;\">");
            out.println("Auto-refresh interval: ");
            out.println("<select name=\"refresh\" onchange=\"this.form.submit()\">");
            int[] intervals = {5, 10, 30, 60, 120};
            for (int interval : intervals) {
                String selected = (interval == refreshInterval) ? "selected" : "";
                out.println("<option value=\"" + interval + "\" " + selected + ">" + interval + " seconds</option>");
            }
            out.println("</select>");
            out.println("</form>");
            out.println("<button onclick=\"exportCSV()\">Export CSV</button>");
            out.println("<button onclick=\"logToFile()\">Log to File</button>");
            out.println("<span id=\"logStatus\"></span>");
            out.println("<span style=\"float: right;\">Last updated: " + new java.util.Date() + "</span>");
            out.println("</div>");
            
            // Get thread information
            ThreadInfo threadInfo = getThreadInformation();

            // Main metrics grid
            out.println("<div class=\"grid\">");
            
            // Tomcat Thread Pool Status
            out.println("<div class=\"metric-card\">");
            out.println("<div class=\"metric-title\">Tomcat HTTP Connector Threads</div>");
            out.println("<div class=\"metric-value " + getStatusClass(threadInfo.httpCurrentThreadsBusy, threadInfo.httpMaxThreads) + "\">");
            out.println(threadInfo.httpCurrentThreadsBusy + " / " + threadInfo.httpMaxThreads + " busy");
            out.println("</div>");
            out.println("<div>Available: " + (threadInfo.httpMaxThreads - threadInfo.httpCurrentThreadsBusy) + "</div>");
            out.println("</div>");
            
            // AJP Thread Pool (if available)
            if (threadInfo.ajpMaxThreads > 0) {
                out.println("<div class=\"metric-card\">");
                out.println("<div class=\"metric-title\">Tomcat AJP Connector Threads</div>");
                out.println("<div class=\"metric-value " + getStatusClass(threadInfo.ajpCurrentThreadsBusy, threadInfo.ajpMaxThreads) + "\">");
                out.println(threadInfo.ajpCurrentThreadsBusy + " / " + threadInfo.ajpMaxThreads + " busy");
                out.println("</div>");
                out.println("<div>Available: " + (threadInfo.ajpMaxThreads - threadInfo.ajpCurrentThreadsBusy) + "</div>");
                out.println("</div>");
            }
            
            // Total System Threads
            out.println("<div class=\"metric-card\">");
            out.println("<div class=\"metric-title\">Total System Threads</div>");
            out.println("<div class=\"metric-value normal\">" + threadInfo.totalThreadCount + "</div>");
            out.println("<div>Peak: " + threadInfo.peakThreadCount + "</div>");
            out.println("</div>");
            
            // Daemon Threads
            out.println("<div class=\"metric-card\">");
            out.println("<div class=\"metric-title\">Daemon Threads</div>");
            out.println("<div class=\"metric-value normal\">" + threadInfo.daemonThreadCount + "</div>");
            out.println("<div>Non-daemon: " + (threadInfo.totalThreadCount - threadInfo.daemonThreadCount) + "</div>");
            out.println("</div>");
            
            out.println("</div>");
            
            // Detailed thread information table
            out.println("<div class=\"thread-details\">");
            out.println("<h2>Thread Pool Details</h2>");
            out.println("<table>");
            out.println("<tr><th>Connector</th><th>Current Busy</th><th>Max Threads</th><th>Available</th><th>Utilization %</th></tr>");
            
            if (threadInfo.httpMaxThreads > 0) {
                double httpUtilization = (double) threadInfo.httpCurrentThreadsBusy / threadInfo.httpMaxThreads * 100;
                out.println("<tr>");
                out.println("<td>HTTP/1.1</td>");
                out.println("<td>" + threadInfo.httpCurrentThreadsBusy + "</td>");
                out.println("<td>" + threadInfo.httpMaxThreads + "</td>");
                out.println("<td>" + (threadInfo.httpMaxThreads - threadInfo.httpCurrentThreadsBusy) + "</td>");
                out.println("<td class=\"" + getUtilizationClass(httpUtilization) + "\">" + String.format("%.1f%%", httpUtilization) + "</td>");
                out.println("</tr>");
            }
            
            if (threadInfo.ajpMaxThreads > 0) {
                double ajpUtilization = (double) threadInfo.ajpCurrentThreadsBusy / threadInfo.ajpMaxThreads * 100;
                out.println("<tr>");
                out.println("<td>AJP/1.3</td>");
                out.println("<td>" + threadInfo.ajpCurrentThreadsBusy + "</td>");
                out.println("<td>" + threadInfo.ajpMaxThreads + "</td>");
                out.println("<td>" + (threadInfo.ajpMaxThreads - threadInfo.ajpCurrentThreadsBusy) + "</td>");
                out.println("<td class=\"" + getUtilizationClass(ajpUtilization) + "\">" + String.format("%.1f%%", ajpUtilization) + "</td>");
                out.println("</tr>");
            }
            
            out.println("</table>");
            out.println("</div>");
            
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");

        } catch (JMException e) {
            log("JMX error retrieving thread information for display", e);
            out.println("<h2>Error retrieving thread information</h2>");
            out.println("<p>Unable to retrieve JMX data from Tomcat. Please check:</p>");
            out.println("<ul>");
            out.println("<li>Tomcat is running properly</li>");
            out.println("<li>JMX is enabled and accessible</li>");
            out.println("<li>Application has proper permissions</li>");
            out.println("</ul>");
            out.println("<p>Error details: " + escapeHtml(e.getMessage()) + "</p>");
        } catch (IOException e) {
            log("I/O error during display rendering", e);
            throw e;
        } catch (Exception e) {
            log("Unexpected error rendering display", e);
            out.println("<h2>Error retrieving thread information</h2>");
            out.println("<p>An unexpected error occurred. Please check the server logs for details.</p>");
            out.println("<p>Error: " + escapeHtml(e.getMessage()) + "</p>");
        }
    }
    
    private ThreadInfo getThreadInformation() throws JMException {
        ThreadInfo info = new ThreadInfo();

        try {
            // Get system thread information
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            info.totalThreadCount = threadMXBean.getThreadCount();
            info.peakThreadCount = threadMXBean.getPeakThreadCount();
            info.daemonThreadCount = threadMXBean.getDaemonThreadCount();

            // Get Tomcat-specific thread pool information
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();

            // Look for HTTP connector
            Set<ObjectName> httpConnectors = server.queryNames(
                new ObjectName("Catalina:type=ThreadPool,name=*http*"), null);

            for (ObjectName name : httpConnectors) {
                try {
                    Object maxThreads = server.getAttribute(name, "maxThreads");
                    Object currentThreadsBusy = server.getAttribute(name, "currentThreadsBusy");

                    if (maxThreads != null && currentThreadsBusy != null) {
                        info.httpMaxThreads = (Integer) maxThreads;
                        info.httpCurrentThreadsBusy = (Integer) currentThreadsBusy;
                        break;
                    }
                } catch (AttributeNotFoundException e) {
                    // Try alternative attribute names
                    try {
                        Object maxThreads = server.getAttribute(name, "maxThreads");
                        Object currentThreadCount = server.getAttribute(name, "currentThreadCount");

                        if (maxThreads != null && currentThreadCount != null) {
                            info.httpMaxThreads = (Integer) maxThreads;
                            info.httpCurrentThreadsBusy = (Integer) currentThreadCount;
                            break;
                        }
                    } catch (Exception ex) {
                        log("Failed to retrieve HTTP connector attributes from: " + name, ex);
                        // Continue to next connector
                    }
                }
            }

            // Look for AJP connector
            Set<ObjectName> ajpConnectors = server.queryNames(
                new ObjectName("Catalina:type=ThreadPool,name=*ajp*"), null);

            for (ObjectName name : ajpConnectors) {
                try {
                    Object maxThreads = server.getAttribute(name, "maxThreads");
                    Object currentThreadsBusy = server.getAttribute(name, "currentThreadsBusy");

                    if (maxThreads != null && currentThreadsBusy != null) {
                        info.ajpMaxThreads = (Integer) maxThreads;
                        info.ajpCurrentThreadsBusy = (Integer) currentThreadsBusy;
                        break;
                    }
                } catch (Exception e) {
                    log("AJP connector query failed (might not be configured): " + e.getMessage());
                    // AJP connector might not be configured, continue
                }
            }

            return info;

        } catch (Exception e) {
            throw new JMException("Failed to retrieve thread information: " + e.getMessage());
        }
    }
    
    private String logThreadDataToFile(ThreadInfo threadInfo) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = dateFormat.format(new Date());

        File logFile = new File(logDirectory, "thread-monitor-" + today + ".csv");
        boolean isNewFile = !logFile.exists();

        // Check if we can write to the directory
        File logDir = new File(logDirectory);
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            if (!created) {
                throw new IOException("Cannot create log directory: " + logDirectory);
            }
        }

        if (!logDir.canWrite()) {
            throw new IOException("Cannot write to log directory: " + logDirectory);
        }

        // Check if log rotation is needed
        if (logFile.exists() && logFile.length() > maxLogFileSize) {
            rotateLogFile(logFile);
            isNewFile = true; // New file after rotation
        }

        try (FileWriter writer = new FileWriter(logFile, true);
             PrintWriter out = new PrintWriter(writer)) {

            // Write header if new file
            if (isNewFile) {
                out.println("Timestamp,HTTP_Busy_Threads,HTTP_Max_Threads,HTTP_Available,HTTP_Utilization_Percent," +
                           "AJP_Busy_Threads,AJP_Max_Threads,AJP_Available,AJP_Utilization_Percent," +
                           "Total_System_Threads,Peak_System_Threads,Daemon_Threads");
            }

            // Write data using shared formatter
            String csvData = formatThreadInfoAsCSVRow(threadInfo);
            out.print(csvData);
        }

        return "Data logged to: " + logFile.getAbsolutePath();
    }

    private void rotateLogFile(File currentFile) throws IOException {
        // Find existing rotation number
        String baseName = currentFile.getName();
        int maxRotation = 0;

        File parentDir = currentFile.getParentFile();
        File[] files = parentDir.listFiles((dir, name) -> name.startsWith(baseName));

        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.matches(baseName + "\\.\\d+")) {
                    String numStr = name.substring(baseName.length() + 1);
                    try {
                        int num = Integer.parseInt(numStr);
                        maxRotation = Math.max(maxRotation, num);
                    } catch (NumberFormatException e) {
                        // Ignore malformed rotation numbers
                    }
                }
            }
        }

        // Rotate existing files
        for (int i = maxRotation; i >= 1; i--) {
            File oldFile = new File(parentDir, baseName + "." + i);
            if (oldFile.exists()) {
                if (i >= maxLogFiles - 1) {
                    // Delete oldest file
                    if (!oldFile.delete()) {
                        log("Failed to delete old log file: " + oldFile.getAbsolutePath());
                    }
                } else {
                    // Rename to higher number
                    File newFile = new File(parentDir, baseName + "." + (i + 1));
                    if (!oldFile.renameTo(newFile)) {
                        log("Failed to rotate log file: " + oldFile.getAbsolutePath());
                    }
                }
            }
        }

        // Rotate current file to .1
        File rotatedFile = new File(parentDir, baseName + ".1");
        if (!currentFile.renameTo(rotatedFile)) {
            throw new IOException("Failed to rotate current log file: " + currentFile.getAbsolutePath());
        }

        log("Log file rotated: " + currentFile.getName() + " -> " + rotatedFile.getName());
    }
    
    private String getStatusClass(int busy, int max) {
        if (max == 0) return "normal";
        double utilization = (double) busy / max * 100;
        if (utilization >= criticalThreshold) return "critical";
        if (utilization >= warningThreshold) return "warning";
        return "normal";
    }

    private String getUtilizationClass(double utilization) {
        if (utilization >= criticalThreshold) return "critical";
        if (utilization >= warningThreshold) return "warning";
        return "normal";
    }

    private String formatThreadInfoAsCSV(ThreadInfo threadInfo) {
        StringBuilder csv = new StringBuilder();

        // CSV Header
        csv.append("Timestamp,HTTP_Busy_Threads,HTTP_Max_Threads,HTTP_Available,HTTP_Utilization_Percent,");
        csv.append("AJP_Busy_Threads,AJP_Max_Threads,AJP_Available,AJP_Utilization_Percent,");
        csv.append("Total_System_Threads,Peak_System_Threads,Daemon_Threads\n");

        // CSV Data
        csv.append(formatThreadInfoAsCSVRow(threadInfo));

        return csv.toString();
    }

    private String formatThreadInfoAsCSVRow(ThreadInfo threadInfo) {
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        double httpUtilization = threadInfo.httpMaxThreads > 0 ?
            (double) threadInfo.httpCurrentThreadsBusy / threadInfo.httpMaxThreads * 100 : 0;
        double ajpUtilization = threadInfo.ajpMaxThreads > 0 ?
            (double) threadInfo.ajpCurrentThreadsBusy / threadInfo.ajpMaxThreads * 100 : 0;

        return String.format("%s,%d,%d,%d,%.2f,%d,%d,%d,%.2f,%d,%d,%d%n",
            timestampFormat.format(new Date()),
            threadInfo.httpCurrentThreadsBusy,
            threadInfo.httpMaxThreads,
            threadInfo.httpMaxThreads - threadInfo.httpCurrentThreadsBusy,
            httpUtilization,
            threadInfo.ajpCurrentThreadsBusy,
            threadInfo.ajpMaxThreads,
            threadInfo.ajpMaxThreads - threadInfo.ajpCurrentThreadsBusy,
            ajpUtilization,
            threadInfo.totalThreadCount,
            threadInfo.peakThreadCount,
            threadInfo.daemonThreadCount
        );
    }

    private String formatThreadInfoAsJSON(ThreadInfo threadInfo) {
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        double httpUtilization = threadInfo.httpMaxThreads > 0 ?
            (double) threadInfo.httpCurrentThreadsBusy / threadInfo.httpMaxThreads * 100 : 0;
        double ajpUtilization = threadInfo.ajpMaxThreads > 0 ?
            (double) threadInfo.ajpCurrentThreadsBusy / threadInfo.ajpMaxThreads * 100 : 0;

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(timestampFormat.format(new Date())).append("\",\n");
        json.append("  \"http\": {\n");
        json.append("    \"busyThreads\": ").append(threadInfo.httpCurrentThreadsBusy).append(",\n");
        json.append("    \"maxThreads\": ").append(threadInfo.httpMaxThreads).append(",\n");
        json.append("    \"availableThreads\": ").append(threadInfo.httpMaxThreads - threadInfo.httpCurrentThreadsBusy).append(",\n");
        json.append("    \"utilizationPercent\": ").append(String.format("%.2f", httpUtilization)).append("\n");
        json.append("  },\n");
        json.append("  \"ajp\": {\n");
        json.append("    \"busyThreads\": ").append(threadInfo.ajpCurrentThreadsBusy).append(",\n");
        json.append("    \"maxThreads\": ").append(threadInfo.ajpMaxThreads).append(",\n");
        json.append("    \"availableThreads\": ").append(threadInfo.ajpMaxThreads - threadInfo.ajpCurrentThreadsBusy).append(",\n");
        json.append("    \"utilizationPercent\": ").append(String.format("%.2f", ajpUtilization)).append("\n");
        json.append("  },\n");
        json.append("  \"system\": {\n");
        json.append("    \"totalThreads\": ").append(threadInfo.totalThreadCount).append(",\n");
        json.append("    \"peakThreads\": ").append(threadInfo.peakThreadCount).append(",\n");
        json.append("    \"daemonThreads\": ").append(threadInfo.daemonThreadCount).append(",\n");
        json.append("    \"nonDaemonThreads\": ").append(threadInfo.totalThreadCount - threadInfo.daemonThreadCount).append("\n");
        json.append("  },\n");
        json.append("  \"thresholds\": {\n");
        json.append("    \"warningPercent\": ").append(warningThreshold).append(",\n");
        json.append("    \"criticalPercent\": ").append(criticalThreshold).append("\n");
        json.append("  }\n");
        json.append("}\n");

        return json.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
    
    private static class ThreadInfo {
        int totalThreadCount = 0;
        int peakThreadCount = 0;
        int daemonThreadCount = 0;
        int httpMaxThreads = 0;
        int httpCurrentThreadsBusy = 0;
        int ajpMaxThreads = 0;
        int ajpCurrentThreadsBusy = 0;
    }
}