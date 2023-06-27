/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.k8saap.migrationtool;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.datastax.oss.k8saap.migrationtool.diff.DiffChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    private static ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder().build());

    @Parameters
    static class MainParams {

        @Parameter(names = { "-h", "--help", }, help = true, description = "Show this help.")
        boolean help;
    }


    public static void main(String[] args) {
        final GenerateCmd generateCmd = new GenerateCmd();
        final DiffCmd diffCmd = new DiffCmd();
        final MainParams mainParams = new MainParams();
        JCommander commander = JCommander.newBuilder()
                .addObject(mainParams)
                .addCommand(generateCmd)
                .addCommand(diffCmd)
                .build();

        commander.parse(args);
        final String command = commander.getParsedCommand();
        if (command == null) {
            exit1(commander);
            return;
        }
        if (mainParams.help) {
            commander.usage();
            return;
        }
        final Runnable cmd;
        switch (command) {
            case "generate":
                cmd = generateCmd;
                break;
            case "diff":
                cmd = diffCmd;
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

    @Parameters(
            commandNames = { "diff" },
            commandDescription = "Check diffs between a Pulsar cluster and a generated PulsarCluster CRD"
    )
    static class DiffCmd implements Runnable {

        @Parameter(names = {"-d", "--dir"}, required = true, description = "Output directory of the generate command.")
        String outputDir;

        @Override
        @SneakyThrows
        public void run() {
            DiffChecker.diffFromDirectory(new File(outputDir));
        }
    }
}
