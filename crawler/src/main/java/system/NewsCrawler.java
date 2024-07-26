package system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.opencsv.CSVWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NewsCrawler {
    private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

    public static void main(String[] args) {
        String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/"; // URL de la página que quieres crawlear
        String csvFolder = "../data/raw"; // Nombre de la carpeta donde se guardará el archivo CSV
        String csvFile = "news.csv"; // Nombre del archivo CSV donde se guardarán las noticias

        try {
            // Crear el directorio si no existe
            Path directory = Paths.get(csvFolder).toAbsolutePath().normalize();
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                logger.info("Directorio creado en: {}", directory);
            } else {
                logger.info("El directorio ya existe en: {}", directory);
            }

            // Preparar la ruta completa al archivo CSV
            Path filePath = Paths.get(csvFolder, csvFile).toAbsolutePath().normalize();
            logger.info("El archivo se guardará en: {}", filePath);

            // Preparar el archivo CSV para escritura
            try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toString()))) {
                Document doc = Jsoup.connect(url).get();
                Elements articlesGames = doc.select("div.row-gap-tablet-3").select("section.mod-description");

                // Escribir encabezados al archivo CSV
                String[] headers = {"titulo","link","cuerpo"};
                csvWriter.writeNext(headers);
                logger.info("Añadiendo los headers: '{}' al archivo {}", headers,csvFile);
                // Iterar sobre los artículos y escribir los datos al CSV
                for (Element article : articlesGames) {
                    if(article.select("span.badge.--sixxs.--arial.com-label.--exclusive-ln").isEmpty()) {
                        Elements txtLink = article.select("h2.com-title.--font-primary.--l.--font-medium");
                        String link = "https://www.lanacion.com.ar" + txtLink.select("a.com-link").attr("href");

                        logger.info("Entrando a la noticia del link {}", link);
                        Document news = Jsoup.connect(link).get();
                        String title = news.select("h1.com-title.--font-primary.--sixxl.--font-extra").text(); // Suponiendo que el título está en un <h2>
                        Elements parrafos = news.select("p.com-paragraph.--s");
                        String cuerpo = parrafos.text(); // Suponiendo que el cuerpo está en un <p>

                        String[] data = {title, link, cuerpo};
                        csvWriter.writeNext(data);
                        logger.info("Añadiendo los datos al archivo {}", csvFile);
                    }
                    else
                        logger.info("Se detectó una noticia exclusiva para miembros. Se saltea la noticia.");
                }

                logger.info("Datos guardados en {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Error al escribir en el archivo CSV", e);
        }
    }
}
