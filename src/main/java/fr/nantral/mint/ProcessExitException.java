package fr.nantral.mint;

class ProcessExitException extends Exception {
    private String[] mCommand;
    private int mExitCode;

    ProcessExitException(String[] command, int exitCode) {
        mCommand = command;
        mExitCode = exitCode;
    }

    String[] getCommand() { return mCommand; }
    int getExitCode() { return mExitCode; }

    public String toString() {
        return "Command " + mCommand[0] + " exited with code " + mExitCode;
    }
}
