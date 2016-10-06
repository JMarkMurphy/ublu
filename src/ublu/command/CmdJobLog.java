/*
 * Copyright (c) 2015, Absolute Performance, Inc. http://www.absolute-performance.com
 * Copyright (c) 2016, Jack J. Woehr jwoehr@softwoehr.com 
 * SoftWoehr LLC PO Box 51, Golden CO 80402-0051 http://www.softwoehr.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package ublu.command;

import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.JobLog;
import com.ibm.as400.access.ObjectDoesNotExistException;
import com.ibm.as400.access.RequestNotSupportedException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import ublu.util.ArgArray;
import ublu.util.DataSink;
import ublu.util.Tuple;

/**
 * Command to manipulate job logs on the server.
 *
 * @author jax
 */
public class CmdJobLog extends Command {

    {
        setNameAndDescription("joblog", "/0 [-as400 @as400] [--,-joblog ~@joblog] [-to datasink] [ -close | -length | -new ~@{jobname} ~@{jobuser} ~@{jobnumber} | -qm ~@{offset} ~@{number} ] : manipulate job logs on the host");
    }

    enum OPS {
        CLOSE, LENGTH, NEW, NOOP, QM
    }

    /**
     * Manipulate job logs on the server
     *
     * @param argArray the remainder of the command stream
     * @return the new remainder of the command stream
     */
    public ArgArray jobLog(ArgArray argArray) {
        Tuple jobLogTuple = null;
        OPS op = OPS.NOOP;
        String jobName = null;
        String jobUser = null;
        String jobNumber = null;
        Integer messageOffset = null;
        Integer numberMessages = null;
        while (argArray.hasDashCommand()) {
            String dashCommand = argArray.parseDashCommand();
            switch (dashCommand) {
                case "-to":
                    String destName = argArray.next();
                    setDataDest(DataSink.fromSinkName(destName));
                    break;
//                case "-from":
//                    String srcName = argArray.next();
//                    setDataSrc(DataSink.fromSinkName(srcName));
//                    break;
                case "-as400":
                    setAs400(getAS400Tuple(argArray.next()));
                    break;
                case "--":
                case "-joblog":
                    jobLogTuple = argArray.nextTupleOrPop();
                    break;
                case "-close":
                    op = OPS.CLOSE;
                    break;
                case "-length":
                    op = OPS.LENGTH;
                    break;
                case "-new":
                    jobName = argArray.nextMaybeQuotationTuplePopString();
                    jobUser = argArray.nextMaybeQuotationTuplePopString();
                    jobNumber = argArray.nextMaybeQuotationTuplePopString();
                    break;
                case "-qm":
                    messageOffset = argArray.nextIntMaybeQuotationTuplePopString();
                    numberMessages = argArray.nextIntMaybeQuotationTuplePopString();
                    break;
                default:
                    unknownDashCommand(dashCommand);
            }
        }
        if (havingUnknownDashCommand()) {
            setCommandResult(COMMANDRESULT.FAILURE);
        } else {
            JobLog jobLog = jobLogFromTuple(jobLogTuple);
            switch (op) {
                case CLOSE:
                    if (jobLog != null) {
                        try {
                            jobLog.close();
                        } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | ObjectDoesNotExistException ex) {
                            getLogger().log(Level.SEVERE, "Couldn't close JobLog instance in " + getNameAndDescription(), ex);
                            setCommandResult(COMMANDRESULT.FAILURE);
                        }
                    } else {
                        getLogger().log(Level.SEVERE, "No JobLog instance for -length in {0}", getNameAndDescription());
                        setCommandResult(COMMANDRESULT.FAILURE);
                    }
                    break;
                case NEW:
                    jobLog = new JobLog(getAs400(), jobName, jobUser, jobNumber);
                     {
                        try {
                            put(jobLog);
                        } catch (SQLException | IOException | AS400SecurityException | ErrorCompletingRequestException | InterruptedException | ObjectDoesNotExistException | RequestNotSupportedException ex) {
                            getLogger().log(Level.SEVERE, "Couldn't put JobLog instance in " + getNameAndDescription(), ex);
                            setCommandResult(COMMANDRESULT.FAILURE);
                        }
                    }
                    break;
                case LENGTH:
                    if (jobLog != null) {
                        try {
                            put(jobLog.getLength());
                        } catch (SQLException | IOException | AS400SecurityException | ErrorCompletingRequestException | InterruptedException | ObjectDoesNotExistException | RequestNotSupportedException ex) {
                            getLogger().log(Level.SEVERE, "Couldn't put JobLog instance in " + getNameAndDescription(), ex);
                            setCommandResult(COMMANDRESULT.FAILURE);
                        }
                    } else {
                        getLogger().log(Level.SEVERE, "No JobLog instance for -length in {0}", getNameAndDescription());
                        setCommandResult(COMMANDRESULT.FAILURE);
                    }
                    break;
                case QM:
                    if (jobLog != null) {
                        try {
                            jobLog.load();
                            put(jobLog.getMessages(messageOffset, numberMessages));
                        } catch (SQLException | IOException | AS400SecurityException | ErrorCompletingRequestException | InterruptedException | ObjectDoesNotExistException | RequestNotSupportedException ex) {
                            getLogger().log(Level.SEVERE, "Couldn't put JobLog instance in " + getNameAndDescription(), ex);
                            setCommandResult(COMMANDRESULT.FAILURE);
                        }
                    } else {
                        getLogger().log(Level.SEVERE, "Error getting or putting queued message list in {0}", getNameAndDescription());
                        setCommandResult(COMMANDRESULT.FAILURE);
                    }
                    break;
                case NOOP:
                    break;
            }
        }
        return argArray;
    }

    private JobLog jobLogFromTuple(Tuple t) {
        JobLog jobLog = null;
        if (t != null) {
            Object o = t.getValue();
            if (o instanceof JobLog) {
                jobLog = JobLog.class.cast(o);
            }
        }
        return jobLog;
    }

    @Override
    public ArgArray cmd(ArgArray args
    ) {
        reinit();
        return jobLog(args);
    }

    @Override
    public COMMANDRESULT getResult() {
        return getCommandResult();
    }
}
