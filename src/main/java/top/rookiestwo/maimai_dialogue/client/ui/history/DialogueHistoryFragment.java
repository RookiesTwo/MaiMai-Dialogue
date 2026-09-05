package top.rookiestwo.maimai_dialogue.client.ui.history;

import java.util.Objects;
import java.util.function.Supplier;

import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;

/**
 * Full-screen history page hosted by the dialogue screen's back stack.
 */
public final class DialogueHistoryFragment extends Fragment {
    private final Supplier<DialogueScreenState> state;
    private final Runnable closed;

    public DialogueHistoryFragment(
            Supplier<DialogueScreenState> state,
            Runnable closed
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.closed = Objects.requireNonNull(closed, "closed");
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        DialogueHistoryView history = new DialogueHistoryView(
                requireContext(),
                () -> getParentFragmentManager().popBackStack()
        );
        var state = this.state.get();
        history.applyTheme(state.theme().orElse(ThemeDefinition.DEFAULT));
        history.render(state.history());
        history.showLatest();
        return history;
    }

    @Override
    public void onDestroyView() {
        closed.run();
        super.onDestroyView();
    }
}
