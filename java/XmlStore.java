import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** The "database": every entity is saved in its own XML file, e.g. xml/products.xml */
public class XmlStore {

    private final String folder;

    public XmlStore(String folder) {
        this.folder = folder;
    }

    /** Read every record of an entity. A record is a map of field name -> text. */
    public synchronized List<Map<String, String>> readAll(String entity) {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            File file = new File(folder, entity + ".xml");
            if (!file.exists()) return list;
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
            NodeList records = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < records.getLength(); i++) {
                Node record = records.item(i);
                if (!(record instanceof Element)) continue;
                Map<String, String> map = new LinkedHashMap<>();
                NodeList fields = record.getChildNodes();
                for (int j = 0; j < fields.getLength(); j++) {
                    Node field = fields.item(j);
                    if (field instanceof Element) map.put(((Element) field).getTagName(), field.getTextContent());
                }
                list.add(map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not read " + entity + ".xml: " + e.getMessage(), e);
        }
        return list;
    }

    private void writeAll(String entity, List<Map<String, String>> list) {
        try {
            new File(folder).mkdirs();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement(entity);
            doc.appendChild(root);
            for (Map<String, String> map : list) {
                Element record = doc.createElement("record");
                for (Map.Entry<String, String> field : map.entrySet()) {
                    Element element = doc.createElement(field.getKey());
                    element.setTextContent(field.getValue());
                    record.appendChild(element);
                }
                root.appendChild(record);
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(folder, entity + ".xml")));
        } catch (Exception e) {
            throw new RuntimeException("Could not write " + entity + ".xml: " + e.getMessage(), e);
        }
    }

    /** First record whose field equals value, or null. */
    public synchronized Map<String, String> findBy(String entity, String field, String value) {
        for (Map<String, String> record : readAll(entity)) {
            if (value != null && value.equals(record.get(field))) return record;
        }
        return null;
    }

    /** Add a new record (no id) or update an existing one (matching id). Returns the saved record. */
    public synchronized Map<String, String> save(String entity, Map<String, String> data) {
        List<Map<String, String>> all = readAll(entity);
        String id = data.get("id");
        Map<String, String> record = null;
        if (id != null && !id.isBlank()) {
            for (Map<String, String> m : all) {
                if (id.equals(m.get("id"))) record = m;
            }
        }
        if (record == null) {
            int max = 0;
            for (Map<String, String> m : all) max = Math.max(max, Integer.parseInt(m.get("id")));
            record = new LinkedHashMap<>();
            record.put("id", String.valueOf(max + 1));
            all.add(record);
        }
        for (Map.Entry<String, String> field : data.entrySet()) {
            // only safe XML tag names are accepted as field names
            if (!field.getKey().equals("id") && field.getKey().matches("[A-Za-z][A-Za-z0-9]*")) {
                record.put(field.getKey(), field.getValue() == null ? "" : field.getValue().trim());
            }
        }
        writeAll(entity, all);
        return record;
    }

    /** Remove the record with this id. */
    public synchronized void delete(String entity, String id) {
        List<Map<String, String>> all = readAll(entity);
        all.removeIf(m -> m.get("id").equals(id));
        writeAll(entity, all);
    }
}
