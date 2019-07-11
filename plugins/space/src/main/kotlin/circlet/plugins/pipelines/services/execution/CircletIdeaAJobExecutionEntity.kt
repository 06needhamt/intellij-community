package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAJobExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graph: AGraphExecutionEntity,
    override val meta: ProjectJob.Process.Container,
    override val autoProvisionVolume: Boolean,
    override val baseVolumeName: String?,
    override val isSnapshotPoint: Boolean,
    override val volumeSize: Int?
) : AContainerExecutionEntity
