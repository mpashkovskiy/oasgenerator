package mpashkovskiy.oasgenerator.springbootexample;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwagerUIController {

//    @GetMapping("/greeting")
    public String greeting(Model model) {
        model.addAttribute("name", "test");
        return "greeting";
    }

}
