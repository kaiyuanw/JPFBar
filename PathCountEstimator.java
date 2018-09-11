package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.Path;
import gov.nasa.jpf.vm.Transition;

import java.io.PrintWriter;
import java.util.Formatter;

public class PathCountEstimator extends ListenerAdapter {
    private final PrintWriter   m_out;
    private final StringBuilder m_buffer    = new StringBuilder();
    private final Formatter     m_formatter = new Formatter(m_buffer);
    private final int           m_logPeriod;
    private       long          m_nextLog;
    private       long          m_startTime;

    private       double        m_progress;
    private       double        m_lastRecordedProgress;
    private       long          m_pathNum;
    private       long          m_lastRecordedPathNum;
    private       boolean       m_backtracked;
    private       long          m_actionNum;

    public PathCountEstimator(Config config)
    {
        m_out       = new PrintWriter(System.out, true);
        m_logPeriod = config.getInt("jpf.path_count_estimator.log_period", 0);
    }

    @Override
    public void searchStarted(Search search)
    {
        m_nextLog     = 0;
        m_startTime   = System.currentTimeMillis();
        m_progress    = 0.0;
        m_lastRecordedProgress = -1;
        m_pathNum     = 0;
        m_lastRecordedPathNum = -1;
        m_backtracked = false;
        m_actionNum   = 0;
        record("SearchStarted", search);
    }

    @Override
    public void searchFinished(Search search)
    {
        log();
        record("SearchFinished", search);
    }

    @Override
    public void stateAdvanced(Search search)
    {
        m_actionNum++;
        // record("StateAdvanced", search);

        if (m_backtracked) {
            if (m_nextLog > System.currentTimeMillis()) {
                return;
            }
            if (log(search)) {
                m_nextLog = m_logPeriod + System.currentTimeMillis();
                record("StateAdvanced", search);
            }
            m_backtracked = false;
        }
        if (search.isEndState() || search.isErrorState() || search.isVisitedState()) {
            m_pathNum++;
            updateProgress(search);
            return;
        }
    }

    @Override
    public void stateBacktracked(Search search)
    {
        m_actionNum++;
        m_backtracked = true;
    }

    private void updateProgress(Search search)
    {
        VM vm = search.getVM();
        Path path = vm.getPath();
        double pathProbability = 1.0;
        for (int i = 0; i < path.size(); i++) {
            Transition transition = path.get(i);
            ChoiceGenerator cg = transition.getChoiceGenerator();
            pathProbability /= cg.getTotalNumberOfChoices();
        }
        m_progress += pathProbability;
    }
    
    private boolean log(Search search)
    {
        if (m_lastRecordedPathNum >= m_pathNum) {
            return false;
        }
        m_lastRecordedPathNum = m_pathNum;

        if (m_progress - m_lastRecordedProgress <= 0.01)
            return false;
        m_lastRecordedProgress = m_progress;

        log();

        return true;
    }

    private void log() {
        long expectedPathNum = (long) (m_pathNum / m_progress);

        long currentTime   = System.currentTimeMillis() - m_startTime;
        long expectedTime  = (long) (currentTime / m_progress);

        m_formatter.format("  [PATH]:  %,d / %,d (%g%%)    Time to finish:  ", m_pathNum, expectedPathNum, 100.0 * m_progress);
        formatTime(expectedTime - currentTime);

        m_out.println(m_buffer.toString());
        m_buffer.setLength(0);
    }

    private void formatTime(long time)
    {
        long days, hours, minutes, seconds;
        boolean commit;

        seconds = time / 1000;
        minutes = seconds / 60;
        hours   = minutes / 60;
        days    = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours   %= 24;

        commit = false;

        if ((commit) || (days != 0))
            {
                commit = true;
                m_buffer.append(days);
                m_buffer.append(" days");
            }

        if ((commit) || (hours != 0))
            {
                if ((commit) && (hours < 10))
                    m_buffer.append('0');

                if (commit) {
                    m_buffer.append(" ");
                }
                m_buffer.append(hours);
                m_buffer.append(" hours");
                commit = true;
            }

        if ((commit) || (minutes != 0))
            {
                if ((commit) && (minutes < 10))
                    m_buffer.append('0');

                if (commit) {
                    m_buffer.append(" ");
                }
                m_buffer.append(minutes);
                m_buffer.append(" minutes");
                commit = true;
            }

        if ((commit) && (seconds < 10))
            m_buffer.append('0');

        if (commit) {
            m_buffer.append(" ");
        }
        m_buffer.append(seconds);
        m_buffer.append(" seconds");
    }

    private static boolean record = true;

    private void record(String msg, Search search)
    {
        if (record) {
            VM vm = search.getVM();
            m_out.println("[RECORD]: " + msg + ", StateId=" + vm.getStateId() + ", ActionId=" + m_actionNum);
        }
    }
}
