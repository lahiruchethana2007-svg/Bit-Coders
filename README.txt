EcoNest - Java + XML + HTML web app
===================================

FOLDERS
  java/   Java code (the server and the business rules)
            EcoNestServer.java   starts the web server, sends the pages
            ApiHandler.java      receives requests from the pages
            XmlStore.java        reads / writes the XML database
            StockService.java    stock in, reorder levels, stock checks
            OrderService.java    creates orders, calculates totals
            DeliveryService.java assigns agents, delivery status, emails
            ReportService.java   monthly report, low-stock alerts
            EmailService.java    email notifications (saved in xml/outbox.xml)
            Json.java            helper that converts data to JSON
  html/   One page per screen (index, products, clients, stock, agents,
          orders, deliveries, reports)
  js/     One script per page + common.js (shared helpers)
  css/    style.css
  xml/    The database: one XML file per table

HOW TO RUN (JDK 11 or newer)
  Windows : double-click run.bat
  Mac/Linux: ./run.sh
  Then open http://localhost:8080

  Run it from inside the econest folder, because the server looks for the
  html, js, css and xml folders next to where it starts.

NOTES
  - The xml files contain a few sample records. Delete them in the app,
    or empty the xml files, when you want to start fresh.
  - Emails are simulated: they are saved in xml/outbox.xml and printed in the
    console. Real sending needs an SMTP library (Jakarta Mail) in EmailService.java.
