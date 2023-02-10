package com.datastax.oss.pulsaroperator.migrationtool;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    private static ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder().build());

    public static void main(String[] args) {
        final GenerateCmd generateCmd = new GenerateCmd();
        JCommander commander = JCommander.newBuilder()
                .addCommand(generateCmd)
                .build();

        commander.parse(args);
        final String command = commander.getParsedCommand();
        if (command == null) {
            exit1(commander);
            return;
        }
        final Runnable cmd;
        switch (command) {
            case "generate":
                cmd = generateCmd;
                break;
            default:
                exit1(commander);
                return;
        }
        cmd.run();
    }

    private static void exit1(JCommander commander) {
        commander.usage();
        System.exit(1);
    }

    @Parameters(
            commandNames = { "generate" },
            commandDescription = "Generate PulsarCluster CRD specs from existing Pulsar cluster"
    )
    static class GenerateCmd implements Runnable {

        @Parameter(names = {"-i", "--input-cluster-specs-file"}, description = "Input cluster specs file.")
        String inputSpecsFile = "bin/input-cluster-specs.yaml";

        @Parameter(names = {"-o", "--output-dir"}, description = "Input cluster specs file.")
        String outputDir = "target";

        @Override
        @SneakyThrows
        public void run() {
            final InputClusterSpecs inputClusterSpecs = getInputClusterSpecs();
            new SpecGenerator(outputDir, inputClusterSpecs)
                    .generate();
            final File fullOut = new File(outputDir, inputClusterSpecs.context);
            DiffChecker.diffFromDirectory(fullOut);
            log.info("Generated specs in {}", fullOut.getAbsolutePath());
        }

        @SneakyThrows
        private InputClusterSpecs getInputClusterSpecs() {
            return yamlMapper.readValue(new File(inputSpecsFile), InputClusterSpecs.class);
        }

    }
}


