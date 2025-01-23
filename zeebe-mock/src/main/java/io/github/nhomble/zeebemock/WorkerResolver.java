package io.github.nhomble.zeebemock;

import java.util.List;

public interface WorkerResolver {

  List<WorkerDefinition> resolve();
}
