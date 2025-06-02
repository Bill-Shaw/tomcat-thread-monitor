package com.monitor.threads;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.AttributeNotFoundException;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

@WebServlet("/thread-monitor")
public class ThreadMonitorServlet extends HttpServlet {
    
    private String logDirectory;
    
    @Override
    public void init() throws ServletException {
        super.init();
        // Try to get log directory from context parameter, otherwise use default
        logDirectory = getServletContext().getInitParameter("logDirectory");
        if (logDirectory == null || logDirectory.trim().isEmpty()) {
            logDirectory = System.getProperty("catalina.home", System.getProperty("user.home")) + "/logs/thread-monitor";
        }
        
        // Create log directory if it doesn't exist
        File logDir = new File(logDirectory);
        if (!logDir.exists()) {
            try {
                boolean created = logDir.mkdirs();
                log("Log directory created: " + logDir.getAbsolutePath() + " (success: " + created + ")");
            } catch (Exception e) {
                log("Failed to create log directory: " + logDir.getAbsolutePath(), e);
            }
        }
        log("Thread monitor initialized. Log directory: " + logDirectory);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if ("export".equals(action)) {
            handleExport(request, response);
        } else if ("log".equals(action)) {
            handleLogToFile(request, response);
        } else {
            handleMonitorDisplay(request, response);
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
            
            // CSV Header
            out.println("Timestamp,HTTP_Busy_Threads,HTTP_Max_Threads,HTTP_Available,HTTP_Utilization_Percent," +
                       "AJP_Busy_Threads,AJP_Max_Threads,AJP_Available,AJP_Utilization_Percent," +
                       "Total_System_Threads,Peak_System_Threads,Daemon_Threads");
            
            // CSV Data
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            double httpUtilization = threadInfo.httpMaxThreads > 0 ? 
                (double) threadInfo.httpCurrentThreadsBusy / threadInfo.httpMaxThreads * 100 : 0;
            double ajpUtilization = threadInfo.ajpMaxThreads > 0 ? 
                (double) threadInfo.ajpCurrentThreadsBusy / threadInfo.ajpMaxThreads * 100 : 0;
            
            out.printf("%s,%d,%d,%d,%.2f,%d,%d,%d,%.2f,%d,%d,%d%n",
                dateFormat.format(new Date()),
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
            
        } catch (Exception e) {
            out.println("Error," + e.getMessage());
            log("Error in export", e);
        }
    }
    
    private void handleLogToFile(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            log("handleLogToFile called from: " + request.getRemoteAddr() + " User-Agent: " + request.getHeader("User-Agent"));
            
            ThreadInfo threadInfo = getThreadInformation();
            String logResult = logThreadDataToFile(threadInfo);
            
            log("Log result: " + logResult);
            
            out.println("{");
            out.println("  \"status\": \"success\",");
            out.println("  \"message\": \"" + logResult.replace("\"", "\\\"") + "\",");
            out.println("  \"timestamp\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\"");
            out.println("}");
            
            // Explicitly flush the response
            out.flush();
            response.flushBuffer();
            
        } catch (Exception e) {
            log("Exception in handleLogToFile: " + e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("{");
            out.println("  \"status\": \"error\",");
            out.println("  \"message\": \"" + e.getMessage().replace("\"", "\\\"") + "\"");
            out.println("}");
            out.flush();
            response.flushBuffer();
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
                } catch (NumberFormatException e) {
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
            
            // Debug info
            out.println("<!-- Debug: Log directory = " + logDirectory + " -->");
            
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
            
        } catch (Exception e) {
            out.println("<h2>Error retrieving thread information:</h2>");
            out.println("<pre>" + e.getMessage() + "</pre>");
            e.printStackTrace(out);
        }
    }
    
    private ThreadInfo getThreadInformation() throws Exception {
        ThreadInfo info = new ThreadInfo();
        
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
                // AJP connector might not be configured
            }
        }
        
        return info;
    }
    
    private String logThreadDataToFile(ThreadInfo threadInfo) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
        
        try (FileWriter writer = new FileWriter(logFile, true);
             PrintWriter out = new PrintWriter(writer)) {
            
            // Write header if new file
            if (isNewFile) {
                out.println("Timestamp,HTTP_Busy_Threads,HTTP_Max_Threads,HTTP_Available,HTTP_Utilization_Percent," +
                           "AJP_Busy_Threads,AJP_Max_Threads,AJP_Available,AJP_Utilization_Percent," +
                           "Total_System_Threads,Peak_System_Threads,Daemon_Threads");
            }
            
            // Write data
            double httpUtilization = threadInfo.httpMaxThreads > 0 ? 
                (double) threadInfo.httpCurrentThreadsBusy / threadInfo.httpMaxThreads * 100 : 0;
            double ajpUtilization = threadInfo.ajpMaxThreads > 0 ? 
                (double) threadInfo.ajpCurrentThreadsBusy / threadInfo.ajpMaxThreads * 100 : 0;
            
            out.printf("%s,%d,%d,%d,%.2f,%d,%d,%d,%.2f,%d,%d,%d%n",
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
        
        return "Data logged to: " + logFile.getAbsolutePath();
    }
    
    private String getStatusClass(int busy, int max) {
        if (max == 0) return "normal";
        double utilization = (double) busy / max;
        if (utilization >= 0.8) return "critical";
        if (utilization >= 0.6) return "warning";
        return "normal";
    }
    
    private String getUtilizationClass(double utilization) {
        if (utilization >= 80) return "critical";
        if (utilization >= 60) return "warning";
        return "normal";
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