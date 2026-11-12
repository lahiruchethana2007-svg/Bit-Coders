import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.net.ssl.SSLSocketFactory;

/** SMTP SSL notifications with XML audit history. Configure using environment variables. */
public class EmailService {
    private final XmlStore store;
    public EmailService(XmlStore store) { this.store = store; }
    private String env(String key) { return System.getenv(key); }
    public boolean configured() { return nonempty(env("ECONEST_SMTP_HOST")) && nonempty(env("ECONEST_SMTP_USER")) && nonempty(env("ECONEST_SMTP_PASSWORD")) && nonempty(env("ECONEST_SMTP_FROM")); }
    private boolean nonempty(String s) { return s != null && !s.isBlank(); }
    public Map<String,String> send(String to, String subject, String body) {
        if (!nonempty(to) || !to.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Recipient email address is missing or invalid.");
        Map<String,String> mail = new LinkedHashMap<>();
        mail.put("toAddress",to); mail.put("subject",subject); mail.put("body",body);
        mail.put("sentAt",LocalDateTime.now().withNano(0).toString().replace('T',' '));
        mail.put("status","Pending");
        if (!configured()) { mail.put("status","Not configured"); mail.put("error","Set ECONEST_SMTP_HOST, ECONEST_SMTP_USER, ECONEST_SMTP_PASSWORD and ECONEST_SMTP_FROM."); }
        else {
            try { smtp(to,subject,body); mail.put("status","Sent"); }
            catch(Exception ex) { mail.put("status","Failed"); mail.put("error",ex.getMessage()==null?"SMTP error":ex.getMessage()); System.err.println("[EMAIL ERROR] "+ex.getMessage()); }
        }
        return store.save("outbox",mail);
    }
    private void smtp(String to,String subject,String body) throws Exception {
        String host=env("ECONEST_SMTP_HOST"), user=env("ECONEST_SMTP_USER"), pass=env("ECONEST_SMTP_PASSWORD"), from=env("ECONEST_SMTP_FROM");
        int port=Integer.parseInt(Optional.ofNullable(env("ECONEST_SMTP_PORT")).filter(s->!s.isBlank()).orElse("465"));
        // SSL SMTP (port 465). Never print credentials or raw SMTP authentication responses.
        try(Socket socket=SSLSocketFactory.getDefault().createSocket(host,port)) {
            socket.setSoTimeout(15000);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            expect(in,220); command(out,in,"EHLO localhost",250);
            command(out,in,"AUTH LOGIN",334);
            command(out,in,Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)),334);
            command(out,in,Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)),235);
            command(out,in,"MAIL FROM:<"+from+">",250);
            command(out,in,"RCPT TO:<"+to+">",250);
            command(out,in,"DATA",354);
            String safeSubject=Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8));
            String encodedBody=Base64.getMimeEncoder(76,"\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(body.getBytes(StandardCharsets.UTF_8));
            String message="From: <"+from+">\r\nTo: <"+to+">\r\nSubject: =?UTF-8?B?"+safeSubject+"?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+encodedBody+"\r\n.\r\n";
            out.write(message);out.flush();expect(in,250);
            out.write("QUIT\r\n");out.flush();
        }
    }
    private void command(BufferedWriter out,BufferedReader in,String value,int expected) throws Exception {out.write(value+"\r\n");out.flush();expect(in,expected);}
    private void expect(BufferedReader in,int code) throws Exception {
        String line; int result=-1;
        do {line=in.readLine();if(line==null)throw new IOException("SMTP server closed connection");if(line.length()<4)throw new IOException("Invalid SMTP response");result=Integer.parseInt(line.substring(0,3));} while(line.charAt(3)=='-');
        if(result!=code)throw new IOException("SMTP rejected request (code "+result+")");
    }
}
