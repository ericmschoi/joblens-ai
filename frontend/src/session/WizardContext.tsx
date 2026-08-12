import { createContext, useContext, useMemo, useReducer, type ReactNode } from 'react';

import { ApiError, toProblemDetail } from '../api/problem';
import { initialState, wizardReducer, type WizardAction, type WizardState } from './WizardState';

/**
 * Session state for the whole flow.
 *
 * React's own reducer rather than a state library: there is exactly one piece of shared state and
 * a handful of transitions, and a dependency would only add indirection.
 */

interface WizardContextValue {
  readonly state: WizardState;
  readonly dispatch: (action: WizardAction) => void;
  /** Runs a request, reporting failure as a problem the user can read and act on. */
  readonly run: <T>(busy: NonNullable<WizardState['busy']>, call: () => Promise<T>,
    onSuccess: (value: T) => void) => Promise<void>;
}

const WizardContext = createContext<WizardContextValue | null>(null);

export function WizardProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(wizardReducer, initialState);

  const value = useMemo<WizardContextValue>(() => ({
    state,
    dispatch,
    run: async (busy, call, onSuccess) => {
      dispatch({ type: 'started', busy });
      try {
        onSuccess(await call());
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          dispatch({ type: 'cancelled' });
          return;
        }
        dispatch({
          type: 'failed',
          problem: error instanceof ApiError ? error.problem : toProblemDetail(null, 0),
        });
      }
    },
  }), [state]);

  return <WizardContext.Provider value={value}>{children}</WizardContext.Provider>;
}

export function useWizard(): WizardContextValue {
  const value = useContext(WizardContext);
  if (!value) {
    throw new Error('useWizard must be used inside a WizardProvider');
  }
  return value;
}
