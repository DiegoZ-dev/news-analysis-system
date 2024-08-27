run: run_java run_notebook

run_java:
	@echo "Ejecutando código Java con Maven..."
	@mvn -f crawler/pom.xml clean compile
	@mvn -f crawler/pom.xml exec:java -Dexec.mainClass="system.NewsCrawler"

run_notebook:
	@echo "Ejecutando notebook Jupyter..."
	@jupyter nbconvert --to notebook --execute --inplace python-etl/transform_data.ipynb
