package system;
import org.slf4j.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.opencsv.CSVWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NewsCrawler
{
	private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

	public static void main(String[] args)
	{
		String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/"; // URL de la página que quieres crawlear
		String csvFolder = "resources"; // Nombre de la carpeta donde se guardará el archivo CSV
		String csvFile = "data.csv"; // Nombre del archivo CSV donde se guardarán las noticias

		try {
			// Crear el directorio si no existe
			Path directory = Paths.get(csvFolder);
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
			}

			// Preparar la ruta completa al archivo CSV
			String filePath = csvFolder + "/" + csvFile;

			// Preparar el archivo CSV para escritura
			FileWriter fileWriter = new FileWriter(filePath);
			CSVWriter csvWriter = new CSVWriter(fileWriter);

			Document doc = Jsoup.connect(url).get();
			Elements articles = doc.select("div.article");

			// Escribir encabezados al archivo CSV
			String[] headers = {"titulo", "cuerpo"};
			csvWriter.writeNext(headers);


			// Iterar sobre los enlaces y escribir los datos al CSV
			for (Element article : articles) {
				String title = article.select("div.h2").text(); // Suponiendo que el título está en un <h2> con clase "title"
				String body = article.select("div.p").text(); // Suponiendo que el cuerpo está en un <div> con clase "body"

				String[] data = {title, body};
				csvWriter.writeNext(data);
			}

			csvWriter.close();
			logger.info("Datos guardados en {}", filePath);

		} catch (IOException e) {
			logger.error("Error al escribir en el archivo CSV", e);
		}
	}
}
