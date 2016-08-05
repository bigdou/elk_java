import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.io.IOException;

import com.x5.template.Theme;
import com.x5.template.Chunk;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;

public class MyClass {
	public static void main(String[] args) throws DockerException, IOException{
		// DockerClient dockerClient = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build();
		
		// use api 1.23
		DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
		  .withDockerHost("unix:///var/run/docker.sock")
		  .withApiVersion("1.23")
		  .build();

		// using jaxrs/jersey implementation here (netty impl is also available)
		DockerCmdExecFactory dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
		  .withConnectTimeout(1000)
		  .withMaxTotalConnections(100)
		  .withMaxPerRouteConnections(10);

		DockerClient dockerClient = DockerClientBuilder.getInstance(config)
		  .withDockerCmdExecFactory(dockerCmdExecFactory)
		  .build();		
		
		Volume volume_elasticsearch = new Volume("/usr/share/elasticsearch/data");
		Volume volume_logstash = new Volume("/opt/logstash/data");
		Volume volume_kibana = new Volume("/opt/kibana/data");

		new File("/volume/elasticsearch/").mkdirs();
		new File("/volume/logstash/").mkdirs();
		new File("/volume/kibana/").mkdirs();
		
		Theme theme = new Theme();
		Chunk chunk_kibana = theme.makeChunk("kibana_template","yml");
		Chunk chunk_logstash = theme.makeChunk("logstash_template","conf");

		// replace static values below with user input
		chunk_kibana.set("host", "localhost");
		chunk_logstash.set("host", "localhost");
		chunk_logstash.set("port", "2103");

		File file_kibana = new File("/volume/kibana/kibana.yml");
		FileWriter out_kibana = new FileWriter(file_kibana);
		chunk_kibana.render(out_kibana);
		out_kibana.flush();
		out_kibana.close();

		File file_logstash = new File("/volume/logstash/ls_telemetry.conf");
		FileWriter out_logstash = new FileWriter(file_logstash);
		chunk_logstash.render(out_logstash);
		out_logstash.flush();
		out_logstash.close();


		File elasticsearchDir = new File("./elasticsearch");
		String elasticsearchID = dockerClient.buildImageCmd(elasticsearchDir)
				.withNoCache(true)
				.withTag("elk_elasticsearch")
				.exec(new BuildImageResultCallback())
				.awaitImageId();		
		Info info = dockerClient.infoCmd().exec();
		System.out.println(info.toString());

		File logstashDir = new File("./logstash");
		String logstashID = dockerClient.buildImageCmd(logstashDir)
				.withNoCache(true)
				.withTag("elk_logstash")
				.exec(new BuildImageResultCallback())
				.awaitImageId();		
		info = dockerClient.infoCmd().exec();
		System.out.println(info.toString());

		File kibanaDir = new File("./kibana");
		String KibanaID = dockerClient.buildImageCmd(kibanaDir)
				.withNoCache(true)
				.withTag("elk_kibana")
				.exec(new BuildImageResultCallback())
				.awaitImageId();		
		info = dockerClient.infoCmd().exec();
		System.out.println(info.toString());

		List<Image> dockerList =  dockerClient.listImagesCmd().exec();
		System.out.println("Search returned" + dockerList.toString());
		
		ExposedPort tcp9200 = ExposedPort.tcp(9200);
		ExposedPort tcp9300 = ExposedPort.tcp(9300);
		Ports portBindings = new Ports();
		portBindings.bind(tcp9200, Ports.Binding.bindPort(9200));
		portBindings.bind(tcp9300, Ports.Binding.bindPort(9300));

		CreateContainerResponse container_elasticsearch = dockerClient.createContainerCmd("elk_elasticsearch")
				.withVolumes(volume_elasticsearch)
				.withName("stack_elk_elasticsearch")
				.withCmd("elasticsearch",
					"-Des.insecure.allow.root=true",
					new String(String.format("--network.host= %s", "127.0.0.1")))
//					"--network.host=127.0.0.1")
				.withPortBindings(portBindings)
				.withBinds(new Bind("/volume/elasticsearch", volume_elasticsearch))
				.exec();

		CreateContainerResponse container_logstash = dockerClient.createContainerCmd("elk_logstash")
				.withVolumes(volume_logstash)
				.withName("stack_elk_logstash")
				.withBinds(new Bind("/volume/logstash", volume_logstash))
				.exec();

		ExposedPort tcp5601 = ExposedPort.tcp(5601);
		Ports portBinding = new Ports();
		portBinding.bind(tcp5601, Ports.Binding.bindPort(5601));

		CreateContainerResponse container_kibana = dockerClient.createContainerCmd("elk_kibana")
				.withVolumes(volume_kibana)
				.withName("stack_elk_kibana")
				.withBinds(new Bind("/volume/kibana", volume_kibana))
				.withPortBindings(portBinding)
				.exec();

		dockerClient.startContainerCmd(container_elasticsearch.getId()).exec();
		dockerClient.startContainerCmd(container_logstash.getId()).exec();
		dockerClient.startContainerCmd(container_kibana.getId()).exec();

		//	InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(container_elasticsearch.getId()).exec();
		//	System.out.println(containerInfo.getState().getStatus());
		//      System.out.println(containerInfo.getState().getFinishedAt());
		//      System.out.println(containerInfo.getState().getExitCode());

	}
}
