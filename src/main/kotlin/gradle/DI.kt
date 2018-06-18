package gradle

import core.datasource.StatsDatasource
import core.datasource.StatusDatasource
import core.entity.*
import core.isAllCommitted
import core.toDocumentList
import core.usecase.*
import data.ConsoleDatasource
import data.RetrofitStatsDatasource
import data.createRetrifotBuildStatsService
import data.findRemoteStatusDatasource


class DI(val config : ConfigEntity = ConfigEntityDefault()) {

    private fun statusDatasources() : List<StatusDatasource> {
        val datasources = mutableListOf<StatusDatasource>(ConsoleDatasource())

        //TODO this block should be moved so it can be tested
        if (config.isPostActivated) {
            if (isAllCommitted()) {
                findRemoteStatusDatasource(config)?.let {
                    datasources.add(it)
                    messageQueue.add(InfoMessage("${it.name().toUpperCase()} config was found"))
                } ?: messageQueue.add(ErrorMessage("No recognized post config was found"))
            } else {
                messageQueue.add(ErrorMessage("You must commit your changes to git before running postChecks"))
            }
        }

        return datasources
    }


    private fun statsDatasources(): List<StatsDatasource> {
        val datasources = mutableListOf<StatsDatasource>()
        if (config.statsBaseUrl.isNotBlank()) {
            val service = createRetrifotBuildStatsService(config.statsBaseUrl, "")
            datasources.add(RetrofitStatsDatasource(service))
        }
        return datasources
    }

    private fun postStatusUseCase() : PostStatusUseCase {
        return PostStatusUseCase(statusDatasources(), messageQueue)
    }

    private fun handleBuildSuccessUseCase() : HandleBuildSuccessUseCase {
        return HandleBuildSuccessUseCase(
                postStatusUseCase(),
                PostStatsUseCase(statsDatasources()),
                config,
                summariesUseCases()
        )
    }

    private fun handleBuildFailedUseCase() : HandleBuildFailedUseCase {
        return HandleBuildFailedUseCase(postStatusUseCase(), summariesUseCases())
    }

    private fun summariesUseCases(): List<GetSummaryUseCase> {
        return listOf(
                GetDurationSummaryUseCase(config),
                GetCoverageSummaryUseCase(
                        config.coberturaReports.toDocumentList(),
                        CreateCoberturaMap(),
                        config.minCoveragePercent),
                GetCoverageSummaryUseCase(
                        config.jacocoReports.toDocumentList(),
                        CreateJacocoMap(),
                        config.minCoveragePercent),
                GetLintSummaryUseCase(
                        config.androidLintReports.toDocumentList() + config.checkstyleReports.toDocumentList(),
                        config.maxLintViolations
                )
        )
    }

    fun handleBuildStartedUseCase() : HandleBuildStartedUseCase {
        return HandleBuildStartedUseCase(postStatusUseCase(), config)
    }

    fun handleBuildFinishedUseCase() : HandleBuildFinishedUseCase {
        return HandleBuildFinishedUseCase(handleBuildFailedUseCase(), handleBuildSuccessUseCase(), config, messageQueue)
    }

    private val messageQueue = mutableListOf<Message>()
}


