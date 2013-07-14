package com.github.jcgay.maven.profiler;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;

public class ProfilerEventSpyTest {

    private ProfilerEventSpy profiler;
    private Table<MavenProject, MojoExecution, Stopwatch> timers;
    private ConcurrentHashMap<MavenProject, Stopwatch> projects;
    private Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        timers = HashBasedTable.create();
        projects = new ConcurrentHashMap<MavenProject, Stopwatch>();
        logger = new ConsoleLogger();

        System.setProperty("profile", "true");
        profiler = new ProfilerEventSpy(
                logger,
                projects,
                timers
        );
    }

    @Test
    public void should_start_a_timer_when_a_mojo_start() throws Exception {

        ExecutionEvent event = aMojoEvent(ExecutionEvent.Type.MojoStarted, aMavenProject("a-project"));

        profiler.onEvent(event);

        assertThat(timers)
                .containsRows(event.getSession().getCurrentProject())
                .containsColumns(event.getMojoExecution());
        assertThat(timers.row(event.getSession().getCurrentProject()).get(event.getMojoExecution()).isRunning())
                .isTrue();
    }

    @Test(dataProvider = "mojo_succeed_and_fail")
    public void should_stop_the_timer_when_a_mojo_succeed_or_fail(ExecutionEvent.Type type) throws Exception {

        ExecutionEvent event = aMojoEvent(type, aMavenProject("a-project"));
        given_event_has_start(event);

        profiler.onEvent(event);

        assertThat(timers)
                .containsRows(event.getSession().getCurrentProject())
                .containsColumns(event.getMojoExecution());

        assertThat(timers.row(event.getSession().getCurrentProject()).get(event.getMojoExecution()).isRunning())
                .isFalse();
        assertThat(timers.row(event.getSession().getCurrentProject()).get(event.getMojoExecution()).elapsedMillis())
                .isPositive();
    }

    @DataProvider
    private Object[][] mojo_succeed_and_fail() {
        return new Object[][] {
                {ExecutionEvent.Type.MojoSucceeded},
                {ExecutionEvent.Type.MojoFailed}
        };
    }

    @Test
    public void should_start_timer_when_project_start() throws Exception {

        ExecutionEvent event = aProjectEvent(ExecutionEvent.Type.ProjectStarted);

        profiler.onEvent(event);

        assertThat(projects.get(event.getSession().getCurrentProject()).isRunning()).isTrue();
    }

    @Test(dataProvider = "project_succeed_and_fail")
    public void should_stop_timer_when_project_fail_or_succeed(ExecutionEvent.Type type) throws Exception {

        given_project_has_start();
        ExecutionEvent event = aProjectEvent(type);

        profiler.onEvent(event);

        assertThat(projects.get(event.getSession().getCurrentProject()).isRunning()).isFalse();
        assertThat(projects.get(event.getSession().getCurrentProject()).elapsedMillis()).isPositive();
    }

    @DataProvider
    private Object[][] project_succeed_and_fail() {
        return new Object[][] {
                {ExecutionEvent.Type.ProjectSucceeded},
                {ExecutionEvent.Type.ProjectFailed}
        };
    }

    @Test
    public void should_not_log_time_when_property_profile_is_not_set() throws Exception {

        // Given
        System.setProperty("profile", "false");

        ExecutionEvent startEvent = aMojoEvent(ExecutionEvent.Type.MojoSucceeded, aMavenProject("a-project"));
        ExecutionEvent endEvent = aMojoEvent(ExecutionEvent.Type.MojoSucceeded, aMavenProject("a-project"));
        ProfilerEventSpy spy = new ProfilerEventSpy(logger, projects, timers);

        // When
        spy.onEvent(startEvent);
        spy.onEvent(endEvent);

        // Then
        assertThat(projects).isEmpty();
        assertThat(timers).isEmpty();
    }

    private static MavenProject aMavenProject(String name) {
        Model model = new Model();
        model.setName(name);
        MavenProject project = new MavenProject(model);
        project.setGroupId("groupId");
        project.setArtifactId("artifactId");
        project.setVersion("1.0");
        return project;
    }

    private void given_project_has_start() throws Exception {
        profiler.onEvent(aProjectEvent(ExecutionEvent.Type.ProjectStarted));
        TimeUnit.MILLISECONDS.sleep(1);
    }

    private void given_event_has_start(ExecutionEvent event) throws Exception {
        profiler.onEvent(aMojoEvent(ExecutionEvent.Type.MojoStarted, event.getMojoExecution(), event.getSession().getCurrentProject()));
        TimeUnit.MILLISECONDS.sleep(1);
    }

    private static ExecutionEvent aMojoEvent(ExecutionEvent.Type type, MavenProject mavenProject) {
        return aMojoEvent(type, new MojoExecution(new Plugin(), "goal", "execution.id"), mavenProject);
    }

    private static ExecutionEvent aMojoEvent(ExecutionEvent.Type type, MojoExecution mojoExecution, MavenProject mavenProject) {
        return new TestExecutionEvent(type, mojoExecution, mavenProject);
    }

    private static ExecutionEvent aProjectEvent(ExecutionEvent.Type type) {
        return aMojoEvent(type, new MojoExecution(new Plugin(), "goal", "execution.id"), aMavenProject("project"));
    }
}

